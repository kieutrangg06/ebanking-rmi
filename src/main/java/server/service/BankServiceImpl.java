package server.service;

import common.models.Account;
import common.models.Bill;
import common.models.Saving;
import common.models.Transaction;
import common.rmi.IBankService;
import common.rmi.IClientCallback;
import server.dao.AccountDAO;
import server.dao.SavingDAO;
import server.db.DatabaseConnection;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BankServiceImpl extends UnicastRemoteObject implements IBankService {
    private static final long serialVersionUID = 1L;

    // DAO quản lý tài khoản & giao dịch lõi (Kiến trúc 3-Tier chuẩn)
    private final AccountDAO accountDAO = new AccountDAO();

    // Quản lý phiên kết nối tập trung trên Server bằng ConcurrentHashMap
    // accountNumber -> Callback
    private final ConcurrentHashMap<String, IClientCallback> onlineClients = new ConcurrentHashMap<>();

    // Map phụ lưu username -> accountNumber để tiện tra cứu phiên
    private final ConcurrentHashMap<String, String> userSessionMap = new ConcurrentHashMap<>();

    // DAO và Tiến trình nền tính lãi của Người 3
    private final SavingDAO savingDAO = new SavingDAO();
    private final InterestCalculatorTask interestCalculatorTask;

    public BankServiceImpl() throws RemoteException {
        super();
        // Khởi động tiến trình quét sinh lãi định kỳ tự động của Người 3
        this.interestCalculatorTask = new InterestCalculatorTask(savingDAO, this::triggerCallback);
        this.interestCalculatorTask.start(10, 15);
    }

    // =========================================================================
    // NGƯỜI 1: AUTHENTICATION & QUẢN LÝ PHIÊN (LOGIN, LOGOUT, REGISTER)
    // =========================================================================

    @Override
    public synchronized Account login(String username, String password, IClientCallback callback) throws RemoteException {
        if (username == null || password == null) return null;
        final String uName = username.trim();
        final String pwd = password.trim();

        try {
            Account acc = accountDAO.authenticate(uName, pwd);
            if (acc != null) {
                if ("LOCKED".equalsIgnoreCase(acc.getStatus())) {
                    System.out.println(">> [LOGIN REJECTED] Tài khoản đang bị khóa: " + uName);
                    return null;
                }

                // Xử lý login trùng: nếu user hoặc tài khoản này đã có phiên online trước đó -> kick phiên cũ
                String oldAcc = userSessionMap.get(uName);
                if (oldAcc != null) {
                    IClientCallback oldCb = onlineClients.remove(oldAcc);
                    if (oldCb != null) {
                        try {
                            oldCb.forceLogout("Tài khoản của bạn đã được đăng nhập từ một phiên làm việc khác.");
                        } catch (RemoteException ignored) {
                            // Client cũ đã ngắt kết nối
                        }
                    }
                    userSessionMap.remove(uName);
                }

                // Nếu có callback đăng ký cùng accountNumber
                IClientCallback existingAccCb = onlineClients.remove(acc.getAccountNumber());
                if (existingAccCb != null && existingAccCb != callback) {
                    try {
                        existingAccCb.forceLogout("Phiên làm việc của bạn đã hết hạn do tài khoản được đăng nhập ở nơi khác.");
                    } catch (RemoteException ignored) {}
                }

                // Đăng ký Callback lắng nghe biến động số dư
                if (callback != null) {
                    onlineClients.put(acc.getAccountNumber(), callback);
                }
                userSessionMap.put(uName, acc.getAccountNumber());
                System.out.println(">> [ONLINE] User: " + uName + " (STK: " + acc.getAccountNumber() + ")");
                return acc;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi xác thực người dùng trong Database: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean register(String username, String password, String fullName, String accountNumber) throws RemoteException {
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty() ||
            fullName == null || fullName.trim().isEmpty() ||
            accountNumber == null || accountNumber.trim().isEmpty()) {
            return false;
        }

        try {
            return accountDAO.createAccount(username.trim(), password.trim(), fullName.trim(), accountNumber.trim());
        } catch (SQLException e) {
            System.err.println("Lỗi đăng ký tài khoản mới: " + e.getMessage());
            return false;
        }
    }

    @Override
    public synchronized void logout(String username) throws RemoteException {
        if (username == null) return;
        final String uName = username.trim();

        String accNum = userSessionMap.remove(uName);
        if (accNum != null) {
            onlineClients.remove(accNum);
            System.out.println("<< [OFFLINE] User: " + uName + " (STK: " + accNum + ")");
        } else {
            // Trường hợp truyền vào STK thay vì username
            onlineClients.remove(uName);
            userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(uName));
            System.out.println("<< [OFFLINE] Session cleanup for: " + uName);
        }
    }

    @Override
    public double getBalance(String accountNumber) throws RemoteException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return -1;
        try {
            return accountDAO.getBalance(accountNumber.trim());
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn số dư tài khoản: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public Account getAccountByNumber(String accountNumber) throws RemoteException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return null;
        try {
            return accountDAO.findByAccountNumber(accountNumber.trim());
        } catch (SQLException e) {
            System.err.println("Lỗi tra cứu thông tin tài khoản: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<String> getOnlineUsers() throws RemoteException {
        return new ArrayList<>(onlineClients.keySet());
    }

    // =========================================================================
    // NGƯỜI 1: CHUYỂN TIỀN ACID & RMI CALLBACK PATTERN
    // =========================================================================

    @Override
    public synchronized boolean transfer(String fromAcc, String toAcc, double amount, String description) throws RemoteException {
        if (amount <= 0 || fromAcc == null || toAcc == null) return false;
        fromAcc = fromAcc.trim();
        toAcc = toAcc.trim();
        if (fromAcc.isEmpty() || toAcc.isEmpty() || fromAcc.equals(toAcc)) return false;

        try {
            boolean success = accountDAO.executeTransfer(fromAcc, toAcc, amount, description);
            if (success) {
                // Thực hiện Callback cho người nhận tiền (Event-driven Notification)
                String cbMsg = "Tài khoản nhận +" + String.format("%,.0f", amount) + " VNĐ từ " + fromAcc
                        + (description != null && !description.isEmpty() ? " (ND: " + description + ")" : "");
                triggerCallback(toAcc, cbMsg);
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Lỗi giao dịch chuyển khoản: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // PHẦN VIỆC CỦA NGƯỜI 2: HÓA ĐƠN & SAO KÊ
    // =========================================================================

    @Override
    public Bill queryBill(String billCode) throws RemoteException {
        String sql = "SELECT * FROM bills WHERE bill_code = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, billCode);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Bill(
                        rs.getInt("id"),
                        rs.getString("bill_code"),
                        rs.getString("service_type"),
                        rs.getString("customer_name"),
                        rs.getDouble("amount"),
                        rs.getString("status")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public synchronized boolean payBill(String accountNumber, String billCode) throws RemoteException {
        Bill bill = queryBill(billCode);
        if (bill == null || "PAID".equalsIgnoreCase(bill.getStatus())) {
            return false;
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Kiểm tra và trừ tiền tài khoản
            String deductSql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?";
            try (PreparedStatement ps = conn.prepareStatement(deductSql)) {
                ps.setDouble(1, bill.getAmount());
                ps.setString(2, accountNumber);
                ps.setDouble(3, bill.getAmount());
                if (ps.executeUpdate() == 0) {
                    conn.rollback();
                    return false; // Số dư không đủ
                }
            }

            // 2. Đánh dấu hóa đơn thành PAID
            String updateBillSql = "UPDATE bills SET status = 'PAID' WHERE bill_code = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateBillSql)) {
                ps.setString(1, billCode);
                ps.executeUpdate();
            }

            // 3. Ghi nhận giao dịch
            String recordTxSql = "INSERT INTO transactions (transaction_type, from_account, to_account, amount, description) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement psTx = conn.prepareStatement(recordTxSql)) {
                psTx.setString(1, "THANH_TOAN_HOA_DON");
                psTx.setString(2, accountNumber);
                psTx.setString(3, billCode);
                psTx.setDouble(4, bill.getAmount());
                psTx.setString(5, "Thanh toan hoa don: " + bill.getServiceType() + " (" + bill.getCustomerName() + ")");
                psTx.executeUpdate();
            }

            conn.commit();
            conn.setAutoCommit(true);
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    @Override
    public List<Transaction> getTransactionHistory(String accountNumber) throws RemoteException {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE from_account = ? OR to_account = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setString(2, accountNumber);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Transaction(
                        rs.getInt("id"),
                        rs.getString("transaction_type"),
                        rs.getString("from_account"),
                        rs.getString("to_account"),
                        rs.getDouble("amount"),
                        rs.getString("description"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // =========================================================================
    // PHẦN VIỆC CỦA NGƯỜI 3: TIẾT KIỆM TỰ ĐỘNG, ADMIN MONITORING & FORCE LOGOUT
    // =========================================================================

    @Override
    public synchronized boolean openSaving(String accountNumber, double amount, double interestRate, int termSeconds) throws RemoteException {
        if (accountNumber == null || accountNumber.trim().isEmpty() || amount <= 0) {
            return false;
        }
        try {
            boolean success = savingDAO.openSaving(accountNumber.trim(), amount, interestRate, termSeconds);
            if (success) {
                triggerCallback(accountNumber.trim(), "Mở sổ tiết kiệm thành công! Số tiền gửi: " +
                        String.format("%,.0f VNĐ", amount) + " (Lãi suất: " + interestRate + "%/kỳ " + termSeconds + "s)");
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Lỗi mở sổ tiết kiệm: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized boolean settleSaving(int savingId) throws RemoteException {
        try {
            SavingDAO.SettleResult result = savingDAO.settleSaving(savingId);
            if (result.isSuccess()) {
                String accNum = result.getAccountNumber();
                double total = result.getTotalRefund();
                triggerCallback(accNum, "Sổ tiết kiệm #" + savingId + " đã tất toán thành công. +" +
                        String.format("%,.0f VNĐ", total) + " (Gốc: " + String.format("%,.0f", result.getPrincipal()) +
                        " + Lãi: " + String.format("%,.0f", result.getInterest()) + ") đã được chuyển về tài khoản.");
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Lỗi tất toán sổ tiết kiệm: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Saving> getSavingsByAccount(String accountNumber) throws RemoteException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return savingDAO.getSavingsByAccount(accountNumber.trim());
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách sổ tiết kiệm: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public synchronized boolean lockAccount(String accountNumber, String reason) throws RemoteException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return false;
        final String accNum = accountNumber.trim();
        try {
            boolean ok = savingDAO.lockAccount(accNum);
            if (ok) {
                // Đá văng client ngay lập tức nếu đang online (Remote Revocation)
                IClientCallback cb = onlineClients.remove(accNum);
                userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(accNum));
                if (cb != null) {
                    try {
                        cb.forceLogout("Tài khoản của bạn đã bị KHÓA bởi Quản trị viên. Lý do: " +
                                (reason != null && !reason.trim().isEmpty() ? reason.trim() : "Vi phạm chính sách ngân hàng"));
                    } catch (RemoteException ignored) {}
                }
                System.out.println(">> [ADMIN ACTION] Đã KHÓA tài khoản STK: " + accNum + " (Lý do: " + reason + ")");
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khóa tài khoản: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public synchronized boolean kickUser(String accountNumber, String reason) throws RemoteException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return false;
        final String accNum = accountNumber.trim();
        IClientCallback cb = onlineClients.remove(accNum);
        userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(accNum));
        if (cb != null) {
            try {
                cb.forceLogout("Bạn đã bị Quản trị viên ngắt kết nối (KICK). Lý do: " +
                        (reason != null && !reason.trim().isEmpty() ? reason.trim() : "Yêu cầu từ quản trị viên"));
                System.out.println(">> [ADMIN ACTION] Đã KICK phiên online của STK: " + accNum + " (Lý do: " + reason + ")");
                return true;
            } catch (RemoteException e) {
                System.err.println("Client đã mất kết nối trước khi nhận lệnh kick: " + accNum);
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean unlockAccount(String accountNumber) throws RemoteException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return false;
        try {
            boolean ok = savingDAO.unlockAccount(accountNumber.trim());
            if (ok) {
                System.out.println(">> [ADMIN ACTION] Đã MỞ KHÓA tài khoản STK: " + accountNumber.trim());
            }
            return ok;
        } catch (SQLException e) {
            System.err.println("Lỗi mở khóa tài khoản: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Account> getAllAccounts() throws RemoteException {
        try {
            return savingDAO.getAllAccounts();
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách tài khoản: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public double getTotalSystemBalance() throws RemoteException {
        try {
            return savingDAO.getTotalSystemBalance();
        } catch (SQLException e) {
            System.err.println("Lỗi tính tổng tiền hệ thống: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }

    public InterestCalculatorTask getInterestCalculatorTask() {
        return interestCalculatorTask;
    }

    // =========================================================================
    // CÁC HÀM BỔ TRỢ HỆ THỐNG: BẢO VỆ CALLBACK CHẾT (DEAD REFERENCE CLEANUP)
    // =========================================================================

    /**
     * Bắn Callback an toàn: Bắt lỗi RemoteException và dọn dẹp các máy khách bị rớt mạng đột ngột (Dead Reference)
     * Đảm bảo Server KHÔNG bị treo luồng (hang thread) hay crash Server.
     */
    private void triggerCallback(String accountNumber, String message) {
        IClientCallback cb = onlineClients.get(accountNumber);
        if (cb != null) {
            try {
                double latestBal = getBalance(accountNumber);
                cb.notifyBalanceChange(message, latestBal);
            } catch (RemoteException e) {
                // Client đã ngắt kết nối bất thường (rút dây mạng, tắt app ngang)
                System.err.println("Gặp Dead Callback Reference tại STK: " + accountNumber + ". Đang dọn dẹp session...");
                onlineClients.remove(accountNumber);
                userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(accountNumber));
            }
        }
    }
}
