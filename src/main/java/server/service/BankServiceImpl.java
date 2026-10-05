package server.service;

import common.models.Account;
import common.models.Bill;
import common.models.Saving;
import common.models.Transaction;
import common.rmi.IBankService;
import common.rmi.IClientCallback;
import server.dao.SavingDAO;
import server.db.DatabaseConnection;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BankServiceImpl extends UnicastRemoteObject implements IBankService {
    private static final long serialVersionUID = 1L;

    // Lưu danh sách client đang online theo STK: accountNumber -> Callback
    // Dùng ConcurrentHashMap để an toàn đa luồng khi nhiều người đăng nhập/đăng xuất cùng lúc
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
    // PHẦN VIỆC CỦA NGƯỜI 1: AUTHENTICATION, QUẢN LÝ PHIÊN & CHUYỂN TIỀN CALLBACK
    // =========================================================================

    @Override
    public synchronized Account login(String username, String password, IClientCallback callback) throws RemoteException {
        String sql = "SELECT * FROM accounts WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String status = rs.getString("status");
                if ("LOCKED".equalsIgnoreCase(status)) {
                    System.out.println("Tài khoản bị khóa: " + username);
                    return null;
                }

                Account acc = new Account(
                        rs.getInt("id"),
                        rs.getString("account_number"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("full_name"),
                        rs.getDouble("balance"),
                        status
                );

                // Đăng ký Callback lắng nghe biến động số dư
                if (callback != null) {
                    onlineClients.put(acc.getAccountNumber(), callback);
                }
                userSessionMap.put(username, acc.getAccountNumber());
                System.out.println(">> [ONLINE] User: " + username + " (STK: " + acc.getAccountNumber() + ")");
                return acc;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean register(String username, String password, String fullName, String accountNumber) throws RemoteException {
        String sql = "INSERT INTO accounts (account_number, username, password, full_name, balance, status) VALUES (?, ?, ?, ?, 0.0, 'ACTIVE')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setString(2, username);
            ps.setString(3, password);
            ps.setString(4, fullName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized void logout(String username) throws RemoteException {
        String accNum = userSessionMap.remove(username);
        if (accNum != null) {
            onlineClients.remove(accNum);
            System.out.println("<< [OFFLINE] User: " + username);
        }
    }

    @Override
    public double getBalance(String accountNumber) throws RemoteException {
        String sql = "SELECT balance FROM accounts WHERE account_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public synchronized boolean transfer(String fromAcc, String toAcc, double amount, String description) throws RemoteException {
        if (amount <= 0 || fromAcc.equals(toAcc)) return false;

        String checkBalSql = "SELECT balance FROM accounts WHERE account_number = ? FOR UPDATE";
        String deductSql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ?";
        String addSql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
        String recordTxSql = "INSERT INTO transactions (transaction_type, from_account, to_account, amount, description) VALUES (?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // BẮT ĐẦU TRANSACTION 3 LỚP

            // 1. Kiểm tra số dư người gửi
            double currentBal = 0.0;
            try (PreparedStatement psCheck = conn.prepareStatement(checkBalSql)) {
                psCheck.setString(1, fromAcc);
                ResultSet rs = psCheck.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    return false;
                }
                currentBal = rs.getDouble("balance");
                if (currentBal < amount) {
                    conn.rollback();
                    return false; // Không đủ tiền
                }
            }

            // 2. Trừ tiền người gửi
            try (PreparedStatement psDeduct = conn.prepareStatement(deductSql)) {
                psDeduct.setDouble(1, amount);
                psDeduct.setString(2, fromAcc);
                psDeduct.executeUpdate();
            }

            // 3. Cộng tiền người nhận
            try (PreparedStatement psAdd = conn.prepareStatement(addSql)) {
                psAdd.setDouble(1, amount);
                psAdd.setString(2, toAcc);
                int rows = psAdd.executeUpdate();
                if (rows == 0) { // Tài khoản nhận không tồn tại
                    conn.rollback();
                    return false;
                }
            }

            // 4. Ghi nhận giao dịch
            try (PreparedStatement psTx = conn.prepareStatement(recordTxSql)) {
                psTx.setString(1, "CHUYEN_TIEN");
                psTx.setString(2, fromAcc);
                psTx.setString(3, toAcc);
                psTx.setDouble(4, amount);
                psTx.setString(5, description);
                psTx.executeUpdate();
            }

            // Chốt giao dịch thành công
            conn.commit();
            conn.setAutoCommit(true);

            // 5. THỰC HIỆN CALLBACK CHO NGƯỜI NHẬN (NẾU ĐANG ONLINE)
            triggerCallback(toAcc, "Tài khoản nhận +" + amount + " VNĐ từ " + fromAcc + " (ND: " + description + ")");
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

    // =========================================================================
    // PHẦN VIỆC CỦA NGƯỜI 2: THANH TOÁN HÓA ĐƠN & XUẤT SAO KÊ
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
            try (PreparedStatement ps = conn.prepareStatement(recordTxSql)) {
                ps.setString(1, "THANH_TOAN_HOA_DON");
                ps.setString(2, accountNumber);
                ps.setString(3, billCode);
                ps.setDouble(4, bill.getAmount());
                ps.setString(5, "Thanh toan hoa don: " + bill.getServiceType() + " (" + bill.getCustomerName() + ")");
                ps.executeUpdate();
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
    public List<String> getOnlineUsers() throws RemoteException {
        return new ArrayList<>(onlineClients.keySet());
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
                System.err.println("Gặp Dead Callback Reference tại STK: " + accountNumber + ". Đang xóa session...");
                onlineClients.remove(accountNumber);
                userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(accountNumber));
            }
        }
    }
}