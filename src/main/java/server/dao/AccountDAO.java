package server.dao;

import common.models.Account;
import server.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AccountDAO {

    /**
     * Xác thực thông tin đăng nhập từ cơ sở dữ liệu
     */
    public Account authenticate(String username, String password) throws SQLException {
        if (username == null || password == null) return null;
        String sql = "SELECT id, account_number, username, password, full_name, balance, status " +
                     "FROM accounts WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, password.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Account(
                            rs.getInt("id"),
                            rs.getString("account_number"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("full_name"),
                            rs.getDouble("balance"),
                            rs.getString("status")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Đăng ký tài khoản mới với số dư mặc định 0.0 và trạng thái ACTIVE
     */
    public boolean createAccount(String username, String password, String fullName, String accountNumber) throws SQLException {
        String sql = "INSERT INTO accounts (account_number, username, password, full_name, balance, status) " +
                     "VALUES (?, ?, ?, ?, 0.0, 'ACTIVE')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber.trim());
            ps.setString(2, username.trim());
            ps.setString(3, password.trim());
            ps.setString(4, fullName.trim());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Tra cứu số dư khả dụng theo số tài khoản
     */
    public double getBalance(String accountNumber) throws SQLException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return -1;
        String sql = "SELECT balance FROM accounts WHERE account_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
            }
        }
        return -1;
    }

    /**
     * Tìm thông tin tài khoản theo số tài khoản (phục vụ hiển thị người nhận, bảo mật che password)
     */
    public Account findByAccountNumber(String accountNumber) throws SQLException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return null;
        String sql = "SELECT id, account_number, username, full_name, balance, status " +
                     "FROM accounts WHERE account_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Account(
                            rs.getInt("id"),
                            rs.getString("account_number"),
                            rs.getString("username"),
                            "", // Ẩn mật khẩu vì lý do an toàn
                            rs.getString("full_name"),
                            rs.getDouble("balance"),
                            rs.getString("status")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Thực hiện chuyển tiền giữa 2 tài khoản với chuẩn ACID Transaction 3 lớp:
     * 1. Sắp xếp thứ tự lock tài khoản cố định theo bảng chữ cái để chống Deadlock đa luồng.
     * 2. Row Lock 'FOR UPDATE' kiểm soát tranh chấp và Race condition.
     * 3. Kiểm tra điều kiện số dư, tài khoản gửi/nhận hoạt động ACTIVE.
     * 4. Double check tại câu UPDATE balance >= amount.
     * 5. Ghi nhận giao dịch vào bảng transactions.
     * 6. Commit nếu thành công, Rollback nếu có bất kỳ lỗi nào.
     */
    public boolean executeTransfer(String fromAcc, String toAcc, double amount, String description) throws SQLException {
        if (amount <= 0 || fromAcc == null || toAcc == null) return false;
        fromAcc = fromAcc.trim();
        toAcc = toAcc.trim();
        if (fromAcc.isEmpty() || toAcc.isEmpty() || fromAcc.equals(toAcc)) return false;

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Bắt đầu ACID Transaction

            // 1. Chống Deadlock: Luôn lock 2 tài khoản theo thứ tự sắp xếp cố định
            String firstLock = fromAcc.compareTo(toAcc) < 0 ? fromAcc : toAcc;
            String secondLock = fromAcc.compareTo(toAcc) < 0 ? toAcc : fromAcc;

            String lockSql = "SELECT account_number, balance, status FROM accounts WHERE account_number = ? FOR UPDATE";

            try (PreparedStatement psLock1 = conn.prepareStatement(lockSql)) {
                psLock1.setString(1, firstLock);
                try (ResultSet rs1 = psLock1.executeQuery()) {
                    if (!rs1.next()) {
                        conn.rollback();
                        return false;
                    }
                }
            }

            try (PreparedStatement psLock2 = conn.prepareStatement(lockSql)) {
                psLock2.setString(1, secondLock);
                try (ResultSet rs2 = psLock2.executeQuery()) {
                    if (!rs2.next()) {
                        conn.rollback();
                        return false;
                    }
                }
            }

            // 2. Kiểm tra tài khoản nguồn và số dư
            String checkSenderSql = "SELECT balance, status FROM accounts WHERE account_number = ?";
            try (PreparedStatement psSender = conn.prepareStatement(checkSenderSql)) {
                psSender.setString(1, fromAcc);
                try (ResultSet rs = psSender.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    String senderStatus = rs.getString("status");
                    if (!"ACTIVE".equalsIgnoreCase(senderStatus)) {
                        conn.rollback();
                        return false; // Tài khoản người gửi bị khóa
                    }
                    double currentBal = rs.getDouble("balance");
                    if (currentBal < amount) {
                        conn.rollback();
                        return false; // Số dư không đủ
                    }
                }
            }

            // 3. Kiểm tra trạng thái tài khoản nhận
            String checkReceiverSql = "SELECT status FROM accounts WHERE account_number = ?";
            try (PreparedStatement psReceiver = conn.prepareStatement(checkReceiverSql)) {
                psReceiver.setString(1, toAcc);
                try (ResultSet rs = psReceiver.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false; // Tài khoản người nhận không tồn tại
                    }
                    String receiverStatus = rs.getString("status");
                    if (!"ACTIVE".equalsIgnoreCase(receiverStatus)) {
                        conn.rollback();
                        return false; // Tài khoản người nhận bị khóa
                    }
                }
            }

            // 4. Trừ tiền tài khoản gửi (kèm điều kiện balance >= amount để bảo vệ kép chống Race Condition)
            String deductSql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?";
            try (PreparedStatement psDeduct = conn.prepareStatement(deductSql)) {
                psDeduct.setDouble(1, amount);
                psDeduct.setString(2, fromAcc);
                psDeduct.setDouble(3, amount);
                int rows = psDeduct.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // 5. Cộng tiền tài khoản nhận
            String addSql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
            try (PreparedStatement psAdd = conn.prepareStatement(addSql)) {
                psAdd.setDouble(1, amount);
                psAdd.setString(2, toAcc);
                int rows = psAdd.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // 6. Ghi nhận nhật ký biến động số dư / giao dịch
            String recordTxSql = "INSERT INTO transactions (transaction_type, from_account, to_account, amount, description) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement psTx = conn.prepareStatement(recordTxSql)) {
                psTx.setString(1, "CHUYEN_TIEN");
                psTx.setString(2, fromAcc);
                psTx.setString(3, toAcc);
                psTx.setDouble(4, amount);
                psTx.setString(5, (description != null && !description.trim().isEmpty()) ? description.trim() : "Chuyen tien");
                psTx.executeUpdate();
            }

            // Hoàn tất transaction nguyên tử
            conn.commit();
            conn.setAutoCommit(true);
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    /**
     * Lấy thông tin tài khoản và KHÓA dòng (SELECT ... FOR UPDATE) trong transaction.
     * Sử dụng trong thanh toán hóa đơn để bảo vệ ACID.
     */
    public Account getAccountByNumberForUpdate(Connection conn, String accountNumber) throws SQLException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return null;
        String sql = "SELECT id, account_number, username, password, full_name, balance, status " +
                     "FROM accounts WHERE account_number = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Account(
                            rs.getInt("id"),
                            rs.getString("account_number"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("full_name"),
                            rs.getDouble("balance"),
                            rs.getString("status")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Trừ tiền tài khoản an toàn với điều kiện balance >= amount.
     */
    public boolean deductBalance(Connection conn, String accountNumber, double amount) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, accountNumber.trim());
            ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cộng tiền tài khoản.
     */
    public boolean addBalance(Connection conn, String accountNumber, double amount) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, accountNumber.trim());
            return ps.executeUpdate() > 0;
        }
    }
}
