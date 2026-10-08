package server.dao;

import common.models.Account;
import common.models.Saving;
import server.db.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object phụ trách nghiệp vụ Sổ Tiết Kiệm và Quản Trị Hệ Thống (Dev 3).
 * Đảm bảo tính toán toàn vẹn ACID, chống Race Condition và Deadlock.
 */
public class SavingDAO {

    public static class SettleResult {
        private final boolean success;
        private final String accountNumber;
        private final double totalRefund;
        private final double principal;
        private final double interest;

        public SettleResult(boolean success, String accountNumber, double totalRefund, double principal, double interest) {
            this.success = success;
            this.accountNumber = accountNumber;
            this.totalRefund = totalRefund;
            this.principal = principal;
            this.interest = interest;
        }

        public boolean isSuccess() { return success; }
        public String getAccountNumber() { return accountNumber; }
        public double getTotalRefund() { return totalRefund; }
        public double getPrincipal() { return principal; }
        public double getInterest() { return interest; }
    }

    /**
     * Mở sổ tiết kiệm trực tuyến:
     * - Trừ tiền từ tài khoản thanh toán (kiểm tra số dư và trạng thái ACTIVE)
     * - Thêm bản ghi sổ tiết kiệm với status = ACTIVE
     * - Ghi nhận lịch sử giao dịch MO_SO_TIET_KIEM
     */
    public boolean openSaving(String accountNumber, double amount, double interestRate, int termSeconds) throws SQLException {
        if (amount <= 0 || accountNumber == null || accountNumber.trim().isEmpty()) {
            return false;
        }
        accountNumber = accountNumber.trim();

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Bắt đầu ACID Transaction

            // 1. Kiểm tra số dư và trạng thái tài khoản với Row Lock
            String checkSql = "SELECT balance, status FROM accounts WHERE account_number = ? FOR UPDATE";
            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setString(1, accountNumber);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    String status = rs.getString("status");
                    if (!"ACTIVE".equalsIgnoreCase(status)) {
                        conn.rollback();
                        return false;
                    }
                    double balance = rs.getDouble("balance");
                    if (balance < amount) {
                        conn.rollback();
                        return false; // Số dư không đủ
                    }
                }
            }

            // 2. Trừ tiền tài khoản nguồn
            String deductSql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?";
            try (PreparedStatement psDeduct = conn.prepareStatement(deductSql)) {
                psDeduct.setDouble(1, amount);
                psDeduct.setString(2, accountNumber);
                psDeduct.setDouble(3, amount);
                int rows = psDeduct.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // 3. Thêm sổ tiết kiệm mới
            String addSavingSql = "INSERT INTO savings (account_number, deposit_amount, interest_rate, term_period, accumulated_interest, status) " +
                                  "VALUES (?, ?, ?, ?, 0.0, 'ACTIVE')";
            try (PreparedStatement psSaving = conn.prepareStatement(addSavingSql)) {
                psSaving.setString(1, accountNumber);
                psSaving.setDouble(2, amount);
                psSaving.setDouble(3, interestRate);
                psSaving.setInt(4, termSeconds);
                psSaving.executeUpdate();
            }

            // 4. Ghi nhận giao dịch
            String recordTxSql = "INSERT INTO transactions (transaction_type, from_account, to_account, amount, description) " +
                                 "VALUES ('MO_SO_TIET_KIEM', ?, 'SAVING', ?, ?)";
            try (PreparedStatement psTx = conn.prepareStatement(recordTxSql)) {
                psTx.setString(1, accountNumber);
                psTx.setDouble(2, amount);
                psTx.setString(3, "Mở sổ tiết kiệm: " + String.format("%,.0f VNĐ", amount) + " (Lãi " + interestRate + "%/kỳ " + termSeconds + "s)");
                psTx.executeUpdate();
            }

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
     * Tất toán sổ tiết kiệm:
     * - Khóa bản ghi sổ tiết kiệm với FOR UPDATE để chống Race Condition với Thread tính lãi
     * - Hoàn trả toàn bộ Tiền gốc + Lãi tích lũy về tài khoản chính
     * - Cập nhật trạng thái sổ tiết kiệm thành CLOSED
     * - Ghi nhận lịch sử giao dịch TAT_TOAN_TIET_KIEM
     */
    public SettleResult settleSaving(int savingId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Bắt đầu ACID Transaction

            // 1. Khóa và kiểm tra sổ tiết kiệm
            String querySql = "SELECT * FROM savings WHERE id = ? AND status = 'ACTIVE' FOR UPDATE";
            String accountNumber;
            double depositAmount;
            double accumulatedInterest;
            double totalRefund;

            try (PreparedStatement psQuery = conn.prepareStatement(querySql)) {
                psQuery.setInt(1, savingId);
                try (ResultSet rs = psQuery.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return new SettleResult(false, null, 0, 0, 0);
                    }
                    accountNumber = rs.getString("account_number");
                    depositAmount = rs.getDouble("deposit_amount");
                    accumulatedInterest = rs.getDouble("accumulated_interest");
                    totalRefund = depositAmount + accumulatedInterest;
                }
            }

            // 2. Hoàn tiền về tài khoản chính
            String refundSql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
            try (PreparedStatement psRefund = conn.prepareStatement(refundSql)) {
                psRefund.setDouble(1, totalRefund);
                psRefund.setString(2, accountNumber);
                psRefund.executeUpdate();
            }

            // 3. Đóng sổ tiết kiệm (CLOSED) để Thread tính lãi không bao giờ cộng thêm
            String closeSql = "UPDATE savings SET status = 'CLOSED' WHERE id = ?";
            try (PreparedStatement psClose = conn.prepareStatement(closeSql)) {
                psClose.setInt(1, savingId);
                psClose.executeUpdate();
            }

            // 4. Ghi nhận giao dịch
            String recordTxSql = "INSERT INTO transactions (transaction_type, from_account, to_account, amount, description) " +
                                 "VALUES ('TAT_TOAN_TIET_KIEM', 'SAVING', ?, ?, ?)";
            try (PreparedStatement psTx = conn.prepareStatement(recordTxSql)) {
                psTx.setString(1, accountNumber);
                psTx.setDouble(2, totalRefund);
                psTx.setString(3, "Tất toán sổ tiết kiệm #" + savingId + " (Gốc: " +
                        String.format("%,.0f", depositAmount) + " + Lãi: " + String.format("%,.0f", accumulatedInterest) + ")");
                psTx.executeUpdate();
            }

            conn.commit();
            conn.setAutoCommit(true);
            return new SettleResult(true, accountNumber, totalRefund, depositAmount, accumulatedInterest);
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
     * Lấy danh sách sổ tiết kiệm của một tài khoản cụ thể
     */
    public List<Saving> getSavingsByAccount(String accountNumber) throws SQLException {
        List<Saving> list = new ArrayList<>();
        String sql = "SELECT * FROM savings WHERE account_number = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSaving(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy toàn bộ sổ tiết kiệm đang ACTIVE (phục vụ tiến trình ngầm tính lãi)
     */
    public List<Saving> getAllActiveSavings() throws SQLException {
        List<Saving> list = new ArrayList<>();
        String sql = "SELECT * FROM savings WHERE status = 'ACTIVE'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapSaving(rs));
            }
        }
        return list;
    }

    /**
     * Lấy toàn bộ sổ tiết kiệm trong hệ thống (dành cho Admin)
     */
    public List<Saving> getAllSavings() throws SQLException {
        List<Saving> list = new ArrayList<>();
        String sql = "SELECT * FROM savings ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapSaving(rs));
            }
        }
        return list;
    }

    /**
     * Cộng dồn lãi suất cho sổ tiết kiệm đang ACTIVE.
     * Trả về số dòng cập nhật. Nếu trả về 0 (sổ đã CLOSED trong lúc tính), không cộng tiền.
     */
    public int addInterest(int savingId, double interestAmount) throws SQLException {
        String sql = "UPDATE savings SET accumulated_interest = accumulated_interest + ? WHERE id = ? AND status = 'ACTIVE'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, interestAmount);
            ps.setInt(2, savingId);
            return ps.executeUpdate();
        }
    }

    // =========================================================================
    // PHẦN ADMIN: QUẢN LÝ TÀI KHOẢN & TỔNG TIỀN HỆ THỐNG
    // =========================================================================

    /**
     * Khóa tài khoản trong CSDL (status = 'LOCKED')
     */
    public boolean lockAccount(String accountNumber) throws SQLException {
        String sql = "UPDATE accounts SET status = 'LOCKED' WHERE account_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Mở khóa tài khoản trong CSDL (status = 'ACTIVE')
     */
    public boolean unlockAccount(String accountNumber) throws SQLException {
        String sql = "UPDATE accounts SET status = 'ACTIVE' WHERE account_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Lấy danh sách toàn bộ tài khoản trong hệ thống
     */
    public List<Account> getAllAccounts() throws SQLException {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT id, account_number, username, full_name, balance, status FROM accounts ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Account(
                        rs.getInt("id"),
                        rs.getString("account_number"),
                        rs.getString("username"),
                        "", // Không để lộ mật khẩu
                        rs.getString("full_name"),
                        rs.getDouble("balance"),
                        rs.getString("status")
                ));
            }
        }
        return list;
    }

    /**
     * Tính tổng số tiền đang lưu hành trong toàn bộ hệ thống
     * (Tổng số dư tài khoản thanh toán + Tổng tiền gốc và lãi sổ tiết kiệm đang ACTIVE)
     */
    public double getTotalSystemBalance() throws SQLException {
        double totalAccountBalance = 0.0;
        double totalSavingBalance = 0.0;

        String accSql = "SELECT COALESCE(SUM(balance), 0) AS total_acc FROM accounts";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(accSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                totalAccountBalance = rs.getDouble("total_acc");
            }
        }

        String savingSql = "SELECT COALESCE(SUM(deposit_amount + accumulated_interest), 0) AS total_saving FROM savings WHERE status = 'ACTIVE'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(savingSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                totalSavingBalance = rs.getDouble("total_saving");
            }
        }

        return totalAccountBalance + totalSavingBalance;
    }

    private Saving mapSaving(ResultSet rs) throws SQLException {
        return new Saving(
                rs.getInt("id"),
                rs.getString("account_number"),
                rs.getDouble("deposit_amount"),
                rs.getDouble("interest_rate"),
                rs.getInt("term_period"),
                rs.getDouble("accumulated_interest"),
                rs.getString("status"),
                rs.getTimestamp("created_at")
        );
    }
}
