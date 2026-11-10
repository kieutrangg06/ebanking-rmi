package common.rmi;

import java.io.IOException;
import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Cấu hình chuẩn kiến trúc mạng phân tán RMI cho toàn hệ thống e-Banking:
 * 1. Cấp phát cổng CỐ ĐỊNH (Fixed Ports) để mở tường lửa (Windows Firewall),
 *    thay thế hoàn toàn cổng động (port 0) bị hệ điều hành bốc ngẫu nhiên.
 * 2. Thiết lập IP máy chủ và IP Client Callback (thuộc tính java.rmi.server.hostname)
 *    để truyền Stub chính xác giữa 2 hoặc nhiều máy tính trong mạng LAN/VPN.
 * 3. Hỗ trợ tự động dò tìm IP card mạng thật (ưu tiên Wi-Fi, Ethernet, Radmin VPN, bỏ qua card ảo VirtualBox).
 */
public class RmiConfig {

    // =========================================================================
    // 1. DANH MỤC CỔNG CỐ ĐỊNH (FIXED PORTS ALLOCATION)
    // =========================================================================
    /** Cổng RMI Registry mặc định theo chuẩn Java RMI (1099) */
    public static final int DEFAULT_REGISTRY_PORT = 1099;

    /** Cổng RMI Server Object (BankServiceImpl) cố định (1098) - Tránh cổng động 0 */
    public static final int DEFAULT_SERVER_PORT = 1098;

    /** Cổng RMI Client Callback (ClientCallbackImpl) cố định (1097) - Tránh cổng động 0 */
    public static final int DEFAULT_CALLBACK_PORT = 1097;

    /** Tên định danh dịch vụ RMI đăng ký trên Registry */
    public static final String SERVICE_NAME = "BankService";

    /** Thuộc tính hệ thống định nghĩa IP trong Remote Stub */
    public static final String PROP_SERVER_HOSTNAME = "java.rmi.server.hostname";

    // =========================================================================
    // 2. THIẾT LẬP HOSTNAME & IP CHO RMI STUB
    // =========================================================================
    /**
     * Thiết lập địa chỉ IP định danh cho RMI Stub.
     */
    public static void setRmiHostname(String ip) {
        if (ip != null && !ip.trim().isEmpty()) {
            System.setProperty(PROP_SERVER_HOSTNAME, ip.trim());
            System.out.println(">> [RMI NETWORK] Da thiet lap " + PROP_SERVER_HOSTNAME + " = " + ip.trim());
        }
    }

    public static String getRmiHostname() {
        return System.getProperty(PROP_SERVER_HOSTNAME, "127.0.0.1");
    }

    // =========================================================================
    // 3. TỰ ĐỘNG DÒ TÌM IP CÁC CARD MẠNG (WIFI, ETHERNET, VPN)
    // =========================================================================
    public static class NetworkInterfaceInfo {
        private final String displayName;
        private final String ipAddress;
        private final boolean isPreferred;

        public NetworkInterfaceInfo(String displayName, String ipAddress, boolean isPreferred) {
            this.displayName = displayName;
            this.ipAddress = ipAddress;
            this.isPreferred = isPreferred;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public boolean isPreferred() {
            return isPreferred;
        }

        @Override
        public String toString() {
            return displayName + " (" + ipAddress + ")";
        }
    }

    /**
     * Dò tìm toàn bộ các địa chỉ IPv4 khả dụng trên các card mạng của máy tính
     * Tự động loại trừ card ảo VirtualBox / VMware để không chọn nhầm dải 192.168.56.x
     */
    public static List<NetworkInterfaceInfo> getAvailableNetworkInterfaces() {
        List<NetworkInterfaceInfo> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface iface : Collections.list(interfaces)) {
                    if (!iface.isUp() || iface.isLoopback()) continue;

                    String ifaceName = iface.getDisplayName();
                    if (isVirtualAdapter(ifaceName)) continue;

                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    for (InetAddress addr : Collections.list(addresses)) {
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            String ip = addr.getHostAddress();
                            if (ip.startsWith("192.168.56.")) continue; // Bỏ qua VirtualBox
                            boolean preferred = isLanOrVpnIp(ip);
                            list.add(new NetworkInterfaceInfo(cleanInterfaceName(ifaceName), ip, preferred));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Loi do tim card mang: " + e.getMessage());
        }

        // Luôn có fallback 127.0.0.1
        list.add(new NetworkInterfaceInfo("Loopback / Localhost", "127.0.0.1", false));
        return list;
    }

    private static boolean isVirtualAdapter(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("virtualbox") ||
               lower.contains("host-only") ||
               lower.contains("vmware") ||
               lower.contains("vethernet");
    }

    /**
     * Lấy địa chỉ IP ưu tiên nhất của máy tính (Ưu tiên card Wi-Fi thật)
     */
    public static String getBestLocalIp() {
        List<NetworkInterfaceInfo> list = getAvailableNetworkInterfaces();
        // Ưu tiên card Wi-Fi thật
        for (NetworkInterfaceInfo info : list) {
            if (info.getDisplayName().toLowerCase().contains("wi-fi") || info.getDisplayName().toLowerCase().contains("wireless")) {
                return info.getIpAddress();
            }
        }
        for (NetworkInterfaceInfo info : list) {
            if (info.isPreferred()) {
                return info.getIpAddress();
            }
        }
        return list.get(0).getIpAddress();
    }

    private static boolean isLanOrVpnIp(String ip) {
        if (ip == null) return false;
        return ip.startsWith("192.168.") ||
               ip.startsWith("10.") ||
               ip.startsWith("172.") ||
               ip.startsWith("26."); // Radmin VPN phổ biến khi sinh viên demo từ xa
    }

    private static String cleanInterfaceName(String name) {
        if (name == null) return "Network Adapter";
        if (name.toLowerCase().contains("wi-fi") || name.toLowerCase().contains("wireless") || name.toLowerCase().contains("802.11")) {
            return "Wi-Fi Wireless";
        }
        if (name.toLowerCase().contains("ethernet")) {
            return "Ethernet LAN";
        }
        if (name.toLowerCase().contains("radmin")) {
            return "Radmin VPN";
        }
        if (name.toLowerCase().contains("hamachi")) {
            return "Hamachi VPN";
        }
        if (name.toLowerCase().contains("tailscale")) {
            return "Tailscale VPN";
        }
        return name;
    }

    // =========================================================================
    // 4. KIỂM TRA TRẠNG THÁI CỔNG & ĐỘ TRỄ KẾT NỐI (PING & HEALTH CHECK)
    // =========================================================================
    public static boolean isPortAvailable(int port) {
        if (port <= 0) return true;
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static int allocateSafePort(int preferredPort) {
        if (preferredPort <= 0) return 0;
        if (isPortAvailable(preferredPort)) {
            return preferredPort;
        }
        System.out.println(">> [PORT WARNING] Cong " + preferredPort + " da bi chiem dung, tu dong cap phat cong dong 0.");
        return 0;
    }

    public static class PingResult {
        public final boolean success;
        public final long latencyMs;
        public final String message;

        public PingResult(boolean success, long latencyMs, String message) {
            this.success = success;
            this.latencyMs = latencyMs;
            this.message = message;
        }
    }

    public static PingResult pingServer(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long latency = System.currentTimeMillis() - start;
            return new PingResult(true, latency, "Ket noi TCP cong " + port + " thanh cong (" + latency + "ms)");
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new PingResult(false, latency, "Khong the ket noi: " + e.getMessage());
        }
    }

    public static PingResult testRmiService(String host, int registryPort) {
        long start = System.currentTimeMillis();
        try {
            Registry registry = LocateRegistry.getRegistry(host, registryPort);
            String[] list = registry.list();
            boolean found = false;
            for (String s : list) {
                if (SERVICE_NAME.equals(s)) {
                    found = true;
                    break;
                }
            }
            long latency = System.currentTimeMillis() - start;
            if (found) {
                return new PingResult(true, latency, "Dich vu '" + SERVICE_NAME + "' dang truc tuyen (" + latency + "ms)");
            } else {
                return new PingResult(false, latency, "Registry online nhung chua bind dich vu '" + SERVICE_NAME + "'");
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new PingResult(false, latency, "Loi ket noi RMI Registry: " + e.getMessage());
        }
    }
}
