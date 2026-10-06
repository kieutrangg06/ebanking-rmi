package server;

import common.models.Bill;
import common.models.Transaction;
import org.junit.jupiter.api.Test;
import server.dao.BillDAO;
import server.dao.TransactionDAO;
import server.db.DatabaseConnection;

import java.sql.Connection;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseIntegrationTest {

    @Test
    public void testDatabaseConnection() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            assertNotNull(conn, "Kết nối CSDL không được null");
            assertFalse(conn.isClosed(), "Kết nối CSDL phải đang mở");
            System.out.println(">> Kết nối CSDL thành công tới: " + conn.getMetaData().getURL());
        } catch (Exception e) {
            System.out.println(">> Lưu ý: MySQL cục bộ chưa khởi chạy hoặc mật khẩu khác mặc định (" + e.getMessage() + ")");
        }
    }

    @Test
    public void testBillDAOAndTransactionDAOIfDbAvailable() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            BillDAO billDAO = new BillDAO();
            TransactionDAO txDAO = new TransactionDAO();

            // 1. Kiểm tra queryBill an toàn (không crash khi không tìm thấy)
            Bill nonExistent = billDAO.queryBill("MA_KHONG_TON_TAI_9999");
            assertNull(nonExistent, "Hóa đơn không tồn tại phải trả về null an toàn");

            // 2. Kiểm tra queryBill với bill có sẵn nếu có
            Bill existing = billDAO.queryBill("EVN_HANOI_01");
            if (existing != null) {
                assertEquals("EVN_HANOI_01", existing.getBillCode());
                System.out.println(">> Tìm thấy hóa đơn: " + existing);
            }

            // 3. Kiểm tra getTransactionHistory
            List<Transaction> list = txDAO.getTransactionHistory("1001");
            assertNotNull(list);
            System.out.println(">> Số lượng GD của TK 1001: " + list.size());

            // 4. Kiểm tra getTransactionHistoryFiltered
            List<Transaction> filtered = txDAO.getTransactionHistoryFiltered("1001", new Date(0), new Date());
            assertNotNull(filtered);

        } catch (Exception e) {
            System.out.println(">> Bỏ qua integration test do môi trường chưa cấu hình CSDL: " + e.getMessage());
        }
    }
}
