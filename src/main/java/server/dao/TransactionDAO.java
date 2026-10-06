package server.dao;

import common.models.Transaction;
import server.db.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Data Access Object phụ trách bảng transactions.
 * Tuân thủ mô hình 3-Tier Architecture.
 */
public class TransactionDAO {

    /**
     * Ghi nhận giao dịch với Connection truyền vào (phục vụ ACID Transaction).
     */
    public boolean insertTransaction(Connection conn, Transaction tx) throws SQLException {
        String sql = "INSERT INTO transactions (transaction_type, from_account, to_account, amount, description) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tx.getTransactionType());
            ps.setString(2, tx.getFromAccount());
            ps.setString(3, tx.getToAccount());
            ps.setDouble(4, tx.getAmount());
            ps.setString(5, tx.getDescription());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        tx.setId(generatedKeys.getInt(1));
                    }
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Lấy toàn bộ lịch sử giao dịch của tài khoản (sắp xếp giảm dần theo thời gian tạo).
     */
    public List<Transaction> getTransactionHistory(String accountNum) {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE from_account = ? OR to_account = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNum);
            ps.setString(2, accountNum);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("TransactionDAO.getTransactionHistory error: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Lọc lịch sử giao dịch theo khoảng ngày (fromDate -> toDate).
     */
    public List<Transaction> getTransactionHistoryFiltered(String accountNum, Date fromDate, Date toDate) {
        List<Transaction> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE (from_account = ? OR to_account = ?)");

        if (fromDate != null) {
            sql.append(" AND created_at >= ?");
        }
        if (toDate != null) {
            sql.append(" AND created_at <= ?");
        }
        sql.append(" ORDER BY created_at DESC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            ps.setString(paramIndex++, accountNum);
            ps.setString(paramIndex++, accountNum);

            if (fromDate != null) {
                Timestamp fromTs = new Timestamp(fromDate.getTime());
                ps.setTimestamp(paramIndex++, fromTs);
            }
            if (toDate != null) {
                // Đảm bảo bao trùm đến hết 23:59:59.999 của toDate
                long toTime = toDate.getTime();
                toTime += (24L * 60 * 60 * 1000 - 1);
                Timestamp toTs = new Timestamp(toTime);
                ps.setTimestamp(paramIndex++, toTs);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("TransactionDAO.getTransactionHistoryFiltered error: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getInt("id"),
                rs.getString("transaction_type"),
                rs.getString("from_account"),
                rs.getString("to_account"),
                rs.getDouble("amount"),
                rs.getString("description"),
                rs.getTimestamp("created_at")
        );
    }
}
