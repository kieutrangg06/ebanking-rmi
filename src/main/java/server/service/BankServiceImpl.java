package server.service;

import common.models.Account;
import common.models.Bill;
import common.models.Saving;
import common.models.Transaction;
import common.rmi.IBankService;
import common.rmi.IClientCallback;
import server.db.DatabaseConnection;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BankServiceImpl extends UnicastRemoteObject implements IBankService {
    private static final long serialVersionUID = 1L;

    // Lưu danh sách client đang online theo STK: accountNumber -> Callback
    // Dùng ConcurrentHashMap để an toàn đa luồng khi nhiều người đăng nhập/đăng xuất cùng lúc
    private final ConcurrentHashMap<String, IClientCallback> onlineClients = new ConcurrentHashMap<>();

    // Map phụ lưu username -> accountNumber để tiện tra cứu phiên
    private final ConcurrentHashMap<String, String> userSessionMap = new ConcurrentHashMap<>();

    // Scheduled Thread Pool để Người 3 chạy tiến trình tính lãi ngầm
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public BankServiceImpl() throws RemoteException {
        super();
        // Khởi động tiến trình quét sinh lãi định kỳ tự động của Người 3
        startInterestCalculatorTask();
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
                onlineClients.put(acc.getAccountNumber(), callback);
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
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Trừ tiền tài khoản thanh toán
            String deductSql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?";
            try (PreparedStatement ps = conn.prepareStatement(deductSql)) {
                ps.setDouble(1, amount);
                ps.setString(2, accountNumber);
                ps.setDouble(3, amount);
                if (ps.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // Mở sổ tiết kiệm
            String addSavingSql = "INSERT INTO savings (account_number, deposit_amount, interest_rate, term_period, status) VALUES (?, ?, ?, ?, 'ACTIVE')";
            try (PreparedStatement ps = conn.prepareStatement(addSavingSql)) {
                ps.setString(1, accountNumber);
                ps.setDouble(2, amount);
                ps.setDouble(3, interestRate);
                ps.setInt(4, termSeconds);
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
    public synchronized boolean settleSaving(int savingId) throws RemoteException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            String querySql = "SELECT * FROM savings WHERE id = ? AND status = 'ACTIVE' FOR UPDATE";
            String accNum = "";
            double totalReturn = 0.0;

            try (PreparedStatement ps = conn.prepareStatement(querySql)) {
                ps.setInt(1, savingId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    return false;
                }
                accNum = rs.getString("account_number");
                totalReturn = rs.getDouble("deposit_amount") + rs.getDouble("accumulated_interest");
            }

            // Hoàn tiền về tài khoản chính
            String refundSql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(refundSql)) {
                ps.setDouble(1, totalReturn);
                ps.setString(2, accNum);
                ps.executeUpdate();
            }

            // Đóng sổ
            String closeSavingSql = "UPDATE savings SET status = 'CLOSED' WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(closeSavingSql)) {
                ps.setInt(1, savingId);
                ps.executeUpdate();
            }

            conn.commit();
            conn.setAutoCommit(true);

            // Bắn callback báo hoàn tất sổ
            triggerCallback(accNum, "Sổ tiết kiệm #" + savingId + " đã tất toán. +" + totalReturn + " VNĐ về tài khoản.");
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
    public List<Saving> getSavingsByAccount(String accountNumber) throws RemoteException {
        List<Saving> list = new ArrayList<>();
        String sql = "SELECT * FROM savings WHERE account_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Saving(
                        rs.getInt("id"),
                        rs.getString("account_number"),
                        rs.getDouble("deposit_amount"),
                        rs.getDouble("interest_rate"),
                        rs.getInt("term_period"),
                        rs.getDouble("accumulated_interest"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<String> getOnlineUsers() throws RemoteException {
        return new ArrayList<>(onlineClients.keySet());
    }

    @Override
    public synchronized boolean lockAccount(String accountNumber, String reason) throws RemoteException {
        String sql = "UPDATE accounts SET status = 'LOCKED' WHERE account_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                // Nếu tài khoản đang online, đá văng ngay lập tức
                IClientCallback cb = onlineClients.remove(accountNumber);
                if (cb != null) {
                    try {
                        cb.forceLogout("Tài khoản của bạn đã bị khóa bởi Quản trị viên. Lý do: " + reason);
                    } catch (RemoteException ignored) {}
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // =========================================================================
    // CÁC HÀM BỔ TRỢ HỆ THỐNG: TIẾN TRÌNH NỀN & BẢO VỆ CALLBACK CHẾT
    // =========================================================================

    /**
     * Bắn Callback an toàn: Bắt lỗi RemoteException và dọn dẹp các máy khách bị rớt mạng đột ngột (Dead Reference)
     */
    private void triggerCallback(String accountNumber, String message) {
        IClientCallback cb = onlineClients.get(accountNumber);
        if (cb != null) {
            try {
                double latestBal = getBalance(accountNumber);
                cb.notifyBalanceChange(message, latestBal);
            } catch (RemoteException e) {
                // Client đã ngắt kết nối bất thường (rút dây mạng, tắt app ngang)
                System.err.println("Gặp Dead Callback Reference tại STK: " + accountNumber + ". Đang xóa...");
                onlineClients.remove(accountNumber);
            }
        }
    }

    /**
     * Tiến trình nền (Multi-threading): Tự động tính lãi cho các sổ tiết kiệm ACTIVE
     */
    private void startInterestCalculatorTask() {
        // Quét mỗi 10 giây một lần để dễ quan sát khi demo
        scheduler.scheduleAtFixedRate(() -> {
            String sql = "SELECT * FROM savings WHERE status = 'ACTIVE'";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String accNum = rs.getString("account_number");
                    double deposit = rs.getDouble("deposit_amount");
                    double rate = rs.getDouble("interest_rate");

                    // Giả lập tính lãi chu kỳ ngắn: tiền lãi = Tiền gửi * (lãi suất / 100)
                    double addedInterest = deposit * (rate / 100.0);

                    // Cập nhật lãi tích lũy vào DB
                    String updateSql = "UPDATE savings SET accumulated_interest = accumulated_interest + ? WHERE id = ?";
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        updatePs.setDouble(1, addedInterest);
                        updatePs.setInt(2, id);
                        updatePs.executeUpdate();
                    }

                    // Thông báo biến động lãi suất về máy khách nếu đang online
                    triggerCallback(accNum, "Tiền lãi từ Sổ #" + id + " vừa phát sinh +" + addedInterest + " VNĐ!");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 10, 20, TimeUnit.SECONDS);
    }
}