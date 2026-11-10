package server;

import common.rmi.IBankService;
import common.rmi.RmiConfig;
import server.db.DatabaseConnection;
import server.service.BankServiceImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Máy Chủ eBanking RMI Server - Triển khai mạng phân tán thực tế (Production LAN / VPN Ready)
 * 1. Cấp phát cổng CỐ ĐỊNH: Registry (1099), Service Object (1098).
 * 2. Thiết lập java.rmi.server.hostname với IP LAN thật để Client từ xa gọi được.
 * 3. Hỗ trợ mở tường lửa (Windows Firewall) không bị chặn cổng động.
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

        // 2. Dò tìm và hiển thị toàn bộ danh sách địa chỉ IP trên các card mạng của máy chủ
        List<RmiConfig.NetworkInterfaceInfo> interfaces = RmiConfig.getAvailableNetworkInterfaces();
        System.out.println(">> [DANH SÁCH CARD MẠNG KHẢ DỤNG TRÊN MÁY CHỦ]:");
        for (int i = 0; i < interfaces.size(); i++) {
            RmiConfig.NetworkInterfaceInfo info = interfaces.get(i);
            String mark = info.isPreferred() ? " [★ KHUYÊN DÙNG CHO LAN/VPN]" : "";
            System.out.printf("   [%d] %-22s: %-15s%s%n", (i + 1), info.getDisplayName(), info.getIpAddress(), mark);
        }

        // Chọn IP hoạt động: Ưu tiên tham số dòng lệnh -> Best LAN IP
        String selectedIp = (configuredIp != null && !configuredIp.isEmpty()) ? configuredIp : RmiConfig.getBestLocalIp();
        System.out.println("---------------------------------------------------------------------------------");
        System.out.println(">> [CẤU HÌNH IP MÁY CHỦ] java.rmi.server.hostname = " + selectedIp);
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

        // 4. Khởi tạo RMI Registry và Service Object trên CỔNG CỐ ĐỊNH (Fixed Ports)
        System.out.println("---------------------------------------------------------------------------------");
        System.out.println(">> [QUY HOẠCH CỔNG MẠNG CỐ ĐỊNH CHỐNG TƯỜNG LỬA (FIREWALL)]:");
        System.out.println("   • Cổng RMI Registry (Tra cứu)     : " + registryPort + " (TCP)");
        System.out.println("   • Cổng RMI Service (BankService)  : " + servicePort + " (TCP CỐ ĐỊNH)");
        System.out.println("   • Cổng Client Callback (Máy khách): " + RmiConfig.DEFAULT_CALLBACK_PORT + " (TCP CỐ ĐỊNH)");
        System.out.println("---------------------------------------------------------------------------------");

        try {
            // Khởi tạo RMI Registry trên registryPort
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(registryPort);
                System.out.println(">> [RMI REGISTRY] Đã tạo mới Registry thành công trên cổng: " + registryPort);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(registryPort);
                System.out.println(">> [RMI REGISTRY] Sử dụng Registry đã có sẵn trên cổng: " + registryPort);
            }

            // Khởi tạo Service Object với cổng CỐ ĐỊNH servicePort
            IBankService bankService = new BankServiceImpl(servicePort);

            // Rebind dịch vụ với tên "BankService"
            registry.rebind(RmiConfig.SERVICE_NAME, bankService);

            System.out.println(">> [RMI SERVICE] Dịch vụ '" + RmiConfig.SERVICE_NAME + "' đã BIND thành công!");
            System.out.println("=================================================================================");
            System.out.println("                   HỆ THỐNG ĐÃ SẴN SÀNG CHO CLIENT KẾT NỐI                       ");
            System.out.println("=================================================================================");
            System.out.println("  📌 HƯỚNG DẪN KẾT NỐI TỪ MÁY KHÁCH (CLIENT PC):");
            System.out.println("     1. Máy Client mở ClientMain");
            System.out.println("     2. Nhập địa chỉ máy chủ: " + selectedIp);
            System.out.println("     3. Cổng Registry       : " + registryPort);
            System.out.println("     4. Tài khoản demo      : usera / 123456 (Khách hàng) hoặc admin / admin123 (Quản trị)");
            System.out.println("  🛡️ ĐẢM BẢO TƯỜNG LỬA:");
            System.out.println("     • Trên máy SERVER: Mở Inbound Rule cho cổng TCP " + registryPort + ", " + servicePort);
            System.out.println("     • Trên máy CLIENT: Mở Inbound Rule cho cổng TCP " + RmiConfig.DEFAULT_CALLBACK_PORT);
            System.out.println("=================================================================================\n");

            // Đăng ký shutdown hook để dọn dẹp
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n>> Đang dừng eBanking RMI Server an toàn...");
            }));

        } catch (Exception e) {
            System.err.println(">> [FATAL ERROR] Lỗi khởi động RMI Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
