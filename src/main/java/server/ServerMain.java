package server;

import common.rmi.IBankService;
import server.db.DatabaseConnection;
import server.service.BankServiceImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.SQLException;

public class ServerMain {
    public static final int RMI_PORT = 1099;
    public static final String SERVICE_NAME = "BankService";

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("     EBANKING RMI SERVER - CORE ARCHITECTURE (DEV 1)             ");
        System.out.println("=================================================================");

        // 1. Kiểm tra kết nối CSDL trước khi kích hoạt RMI Service
        try (Connection conn = DatabaseConnection.getConnection()) {
            System.out.println(">> [DATABASE] Ket noi MariaDB/MySQL thanh cong (ebanking_db).");
        } catch (SQLException e) {
            System.err.println(">> [DATABASE WARNING] Khong the ket noi co so du lieu: " + e.getMessage());
            System.err.println(">> Vui long dam bao XAMPP/MySQL dang chay tren port 3306.");
        }

        try {
            // 2. Tạo hoặc kết nối RMI Registry trên cổng chuẩn 1099 an toàn
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(RMI_PORT);
                System.out.println(">> [RMI REGISTRY] Khoi tao thanh cong Registry tai cong " + RMI_PORT + ".");
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(RMI_PORT);
                System.out.println(">> [RMI REGISTRY] Ket noi RMI Registry co san tai cong " + RMI_PORT + ".");
            }

            // 3. Khởi tạo đối tượng xử lý nghiệp vụ ngân hàng lõi
            IBankService bankService = new BankServiceImpl();

            // 4. Đăng ký dịch vụ với tên định danh "BankService"
            registry.rebind(SERVICE_NAME, bankService);
            System.out.println(">> [RMI SERVICE] Dich vu '" + SERVICE_NAME + "' da san sang tiep nhan ket noi tu Client.");
            System.out.println("=================================================================\n");

            // 5. Dang ky Shutdown Hook de don dep an toan
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n>> Dang dung eBanking RMI Server...");
            }));

        } catch (Exception e) {
            System.err.println(">> [FATAL ERROR] Loi khoi dong RMI Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
