package server.dao;

import common.models.Bill;
import server.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object phụ trách bảng bills.
 * Tuân thủ mô hình 3-Tier Architecture.
 */
public class BillDAO {

    /**
     * Tìm kiếm hóa đơn theo mã hóa đơn.
     * Trả về Bill nếu tìm thấy, null nếu không tìm thấy (an toàn, không crash Server).
     */
    public Bill queryBill(String billCode) {
        if (billCode == null || billCode.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT * FROM bills WHERE bill_code = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, billCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBill(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("BillDAO.queryBill error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Khóa dòng hóa đơn bằng SELECT ... FOR UPDATE trong Transaction.
     * Ngăn chặn tình trạng Double Payment và Concurrency race condition.
     */
    public Bill queryBillForUpdate(Connection conn, String billCode) throws SQLException {
        if (billCode == null || billCode.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT * FROM bills WHERE bill_code = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, billCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBill(rs);
                }
            }
        }
        return null;
    }

    /**
     * Cập nhật trạng thái hóa đơn với Connection truyền vào (phục vụ ACID Transaction).
     */
    public boolean updateBillStatus(Connection conn, String billCode, String status) throws SQLException {
        String sql = "UPDATE bills SET status = ? WHERE bill_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, billCode);
            return ps.executeUpdate() > 0;
        }
    }

    private Bill mapResultSetToBill(ResultSet rs) throws SQLException {
        return new Bill(
                rs.getInt("id"),
                rs.getString("bill_code"),
                rs.getString("service_type"),
                rs.getString("customer_name"),
                rs.getDouble("amount"),
                rs.getString("status")
        );
    }
}
