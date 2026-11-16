package common.rmi;

import java.io.IOException;            // Xử lý ngoại lệ vào/ra khi kiểm tra cổng Socket
import java.net.Inet4Address;          // Lớp chuẩn theo slide bài giảng đại diện cho địa chỉ IPv4
import java.net.InetAddress;           // Lớp chuẩn theo slide bài giảng đại diện cho địa chỉ IP
import java.net.NetworkInterface;      // Lớp chuẩn theo slide bài giảng đại diện cho card mạng vật lý
import java.net.ServerSocket;          // Socket máy chủ dùng để kiểm tra cổng có bị chiếm dụng không
import java.rmi.registry.LocateRegistry; // Dùng để kết nối hoặc tra cứu bộ danh bạ RMI Registry
import java.rmi.registry.Registry;       // Giao diện điều khiển bộ danh bạ RMI Registry
import java.util.Collections;          // Tiện ích duyệt danh sách theo chuẩn Java Collection
import java.util.Enumeration;          // Kiểu tập hợp danh sách card mạng theo chuẩn Java Networking API

/**
 * Lớp cấu hình mạng cho toàn bộ hệ thống e-Banking RMI (Chuẩn theo Slide bài giảng Lập trình mạng):
 * - Cố định 3 cổng TCP (1099, 1098, 1097) để mở tường lửa Windows dễ dàng.
 * - Sử dụng NetworkInterface và InetAddress để lấy địa chỉ IPv4 Wi-Fi / LAN của máy tính.
 * - Hỗ trợ cấp phát cổng an toàn và đo độ trễ mạng (Ping) trên giao diện.
 */
public class RmiConfig {

    // Cổng RMI Registry mặc định theo chuẩn Java RMI để đăng ký và tìm kiếm dịch vụ
    public static final int DEFAULT_REGISTRY_PORT = 1099;

    // Cổng cố định cho Server Object (BankServiceImpl) - Tránh bị hệ điều hành bốc cổng ngẫu nhiên
    public static final int DEFAULT_SERVER_PORT = 1098;

    // Cổng cố định cho Client Callback (ClientCallbackImpl) để Server gọi ngược về máy Client
    public static final int DEFAULT_CALLBACK_PORT = 1097;

    // Tên định danh của dịch vụ ngân hàng đăng ký trên bộ danh bạ Registry
    public static final String SERVICE_NAME = "BankService";

    // Thuộc tính hệ thống của JVM dùng để định nghĩa địa chỉ IP nhúng vào bên trong Remote Stub
    public static final String PROP_SERVER_HOSTNAME = "java.rmi.server.hostname";

    // =========================================================================
    // THIẾT LẬP IP CHO REMOTE STUB (TRUYỀN ĐÚNG IP QUA MẠNG WI-FI / LAN)
    // =========================================================================
    /**
     * Gán IP mạng thật vào thuộc tính hệ thống để Client từ xa nhận đúng địa chỉ gọi về Server
     */
    public static void setRmiHostname(String ip) {
        if (ip != null && !ip.trim().isEmpty()) {
            System.setProperty(PROP_SERVER_HOSTNAME, ip.trim());
        }
    }

    // =========================================================================
    // TỰ ĐỘNG LẤY ĐỊA CHỈ IP LAN / WI-FI (CHUẨN SLIDE BÀI GIẢNG JAVA NETWORKING)
    // =========================================================================
    /**
     * Quét các card mạng trên máy tính bằng NetworkInterface và InetAddress theo chuẩn bài giảng:
     * - Duyệt qua danh sách NetworkInterface.getNetworkInterfaces()
     * - Bỏ qua card bị tắt (!isUp) hoặc card loopback nội bộ (isLoopback)
     * - Ưu tiên chọn card mạng không dây Wi-Fi đang kết nối để lấy IPv4
     */
    public static String getLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                String fallbackIp = null;
                for (NetworkInterface iface : Collections.list(interfaces)) {
                    // Bỏ qua card mạng đang bị tắt hoặc loopback nội bộ (127.0.0.1)
                    if (!iface.isUp() || iface.isLoopback()) continue;

                    // Nhận diện card mạng Wi-Fi
                    String name = (iface.getName() + " " + iface.getDisplayName()).toLowerCase();
                    boolean isWifi = name.contains("wi-fi") || name.contains("wifi") || name.contains("wlan") || name.contains("wireless");

                    for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            String ip = addr.getHostAddress();
                            // Ưu tiên số 1: Trả về ngay IP của card Wi-Fi
                            if (isWifi) {
                                return ip;
                            }
                            if (fallbackIp == null) {
                                fallbackIp = ip;
                            }
                        }
                    }
                }
                if (fallbackIp != null) return fallbackIp;
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    /**
     * Hàm phụ gọi lại getLocalIp() để tương thích với các màn hình LoginForm, RegisterForm
     */
    public static String getBestLocalIp() {
        return getLocalIp();
    }

    // =========================================================================
    // CẤP PHÁT CỔNG AN TOÀN CHỐNG XUNG ĐỘT (PORT ALLOCATION)
    // =========================================================================
    /**
     * Cấp phát cổng an toàn: Nếu cổng 1097 đã bị chiếm thì tự bốc cổng ngẫu nhiên 0 để không bị crash
     */
    public static int allocateSafePort(int preferredPort) {
        // Nếu cổng yêu cầu <= 0 thì cho phép hệ thống tự bốc cổng ngẫu nhiên 0
        if (preferredPort <= 0) return 0;

        // Thử mở một ServerSocket trên cổng yêu cầu để kiểm tra cổng có đang rảnh không
        try (ServerSocket ss = new ServerSocket(preferredPort)) {
            // Cho phép tái sử dụng địa chỉ cổng nếu vừa đóng
            ss.setReuseAddress(true);
            // Cổng hoàn toàn rảnh rỗi -> Sử dụng đúng số cổng này (ví dụ: 1097)
            return preferredPort;
        } catch (IOException e) {
            // Cổng đã bị tiến trình khác chiếm dụng -> Trả về 0 để RMI tự chọn cổng ngẫu nhiên
            return 0;
        }
    }

    // =========================================================================
    // ĐỐI TƯỢNG VÀ HÀM ĐO ĐỘ TRỄ KẾT NỐI (PING & HEALTH CHECK)
    // =========================================================================
    /**
     * Lớp đóng gói kết quả kiểm tra kết nối mạng RMI
     */
    public static class PingResult {
        public final boolean success; // Trạng thái: true nếu kết nối thành công, false nếu thất bại
        public final long latencyMs;  // Độ trễ phản hồi tính bằng mili-giây (ms)
        public final String message;  // Chuỗi thông báo trạng thái hiển thị lên giao diện

        public PingResult(boolean success, long latencyMs, String message) {
            this.success = success;
            this.latencyMs = latencyMs;
            this.message = message;
        }
    }

    /**
     * Thử kết nối tới RMI Registry của Server và đo độ trễ (phục vụ nút bấm Ping trên LoginForm)
     */
    public static PingResult testRmiService(String host, int registryPort) {
        // Ghi lại mốc thời gian bắt đầu gửi yêu cầu kiểm tra (mili-giây)
        long start = System.currentTimeMillis();
        try {
            // Thử kết nối tới bộ danh bạ RMI Registry tại địa chỉ IP và Cổng chỉ định
            Registry registry = LocateRegistry.getRegistry(host, registryPort);

            // Quét danh sách các tên dịch vụ đã được Server đăng ký trên Registry
            for (String s : registry.list()) {
                // Nếu tìm thấy đúng tên dịch vụ "BankService"
                if (SERVICE_NAME.equals(s)) {
                    // Tính độ trễ mạng = thời gian hiện tại - thời gian bắt đầu
                    long latency = System.currentTimeMillis() - start;
                    // Trả về kết quả thành công kèm số mili-giây
                    return new PingResult(true, latency, "Dịch vụ '" + SERVICE_NAME + "' online (" + latency + "ms)");
                }
            }
            // Kết nối được Registry nhưng Server chưa kịp đăng ký tên dịch vụ
            return new PingResult(false, System.currentTimeMillis() - start, "Registry online nhưng chưa bind " + SERVICE_NAME);
        } catch (Exception e) {
            // Bị lỗi mạng, sai địa chỉ IP, Server chưa bật hoặc bị tường lửa chặn
            return new PingResult(false, System.currentTimeMillis() - start, "Lỗi: " + e.getMessage());
        }
    }
}
