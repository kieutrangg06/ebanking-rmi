package server;

import common.rmi.IBankService;
import common.rmi.RmiConfig;
import server.db.DatabaseConnection;
import server.service.BankServiceImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Máy Chủ eBanking RMI Server:
 * 1. Cấp phát cổng cố định: Registry (1099), Service Object (1098).
 * 2. Thiết lập java.rmi.server.hostname với IP LAN / Wi-Fi để Client kết nối.
 */
public class ServerMain {

    public static void main(String[] args) {
        System.out.println("=================================================================================");
        System.out.println("            EBANKING RMI SERVER - DISTRIBUTED DEPLOYMENT SYSTEM                 ");
        System.out.println("=================================================================================");

        // 1. Phân tích tham số dòng lệnh (nếu có): -ip <ip> -regPort <port> -srvPort <port>
        String configuredIp = null;
        int registryPort = RmiConfig.DEFAULT_REGISTRY_PORT; // 1099
        int servicePort = RmiConfig.DEFAULT_SERVER_PORT;    // 1098

        for (int i = 0; i < args.length; i++) {
            if ("-ip".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                configuredIp = args[++i].trim();
            } else if ("-regPort".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try { registryPort = Integer.parseInt(args[++i].trim()); } catch (Exception ignored) {}
            } else if ("-srvPort".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try { servicePort = Integer.parseInt(args[++i].trim()); } catch (Exception ignored) {}
            }
        }

        // 2. Lấy IP LAN / Wi-Fi của máy chủ
        String selectedIp = (configuredIp != null && !configuredIp.isEmpty()) ? configuredIp : RmiConfig.getLocalIp();
        System.out.println(">> [IP MÁY CHỦ] java.rmi.server.hostname = " + selectedIp);
        RmiConfig.setRmiHostname(selectedIp);

        // 3. Khởi tạo Database MariaDB/MySQL tự động
        System.out.println(">> [DATABASE] Đang kiểm tra và đồng bộ cấu trúc cơ sở dữ liệu ebanking_db...");
        DatabaseConnection.initializeDatabaseIfPossible();

        try (Connection conn = DatabaseConnection.getConnection()) {
            System.out.println(">> [DATABASE] Kết nối CSDL MariaDB/MySQL thành công.");
        } catch (SQLException e) {
            System.err.println(">> [DATABASE WARNING] Không thể kết nối CSDL MySQL: " + e.getMessage());
            System.err.println(">> Hãy đảm bảo XAMPP/MySQL đang bật (Port 3306).");
        }

        // 4. Khởi tạo RMI Registry và Service Object trên cổng cố định
        System.out.println("---------------------------------------------------------------------------------");
        System.out.println(">> [QUY HOẠCH CỔNG MẠNG CỐ ĐỊNH CHỐNG TƯỜNG LỬA (FIREWALL)]:");
        System.out.println("   • Cổng RMI Registry (Tra cứu)     : " + registryPort + " (TCP)");
        System.out.println("   • Cổng RMI Service (BankService)  : " + servicePort + " (TCP CỐ ĐỊNH)");
        System.out.println("   • Cổng Client Callback (Máy khách): " + RmiConfig.DEFAULT_CALLBACK_PORT + " (TCP CỐ ĐỊNH)");
        System.out.println("---------------------------------------------------------------------------------");

        try {
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(registryPort);
                System.out.println(">> [RMI REGISTRY] Đã tạo mới Registry thành công trên cổng: " + registryPort);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(registryPort);
                System.out.println(">> [RMI REGISTRY] Sử dụng Registry đã có sẵn trên cổng: " + registryPort);
            }

            IBankService bankService = new BankServiceImpl(servicePort);
            registry.rebind(RmiConfig.SERVICE_NAME, bankService);

            System.out.println(">> [RMI SERVICE] Dịch vụ '" + RmiConfig.SERVICE_NAME + "' đã BIND thành công!");
            System.out.println("=================================================================================");
            System.out.println("                   HỆ THỐNG ĐÃ SẴN SÀNG CHO CLIENT KẾT NỐI                       ");
            System.out.println("=================================================================================");
            System.out.println("  📌 HƯỚNG DẪN KẾT NỐI TỪ MÁY KHÁCH (CLIENT PC):");
            System.out.println("     1. Máy Client mở ClientMain");
            System.out.println("     2. Nhập địa chỉ máy chủ: " + selectedIp);
            System.out.println("     3. Cổng Registry       : " + registryPort);
            System.out.println("     4. Tài khoản demo      : usera / 123456 hoặc admin / admin123");
            System.out.println("=================================================================================\n");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n>> Đang dừng eBanking RMI Server an toàn...");
            }));

        } catch (Exception e) {
            System.err.println(">> [FATAL ERROR] Lỗi khởi động RMI Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
