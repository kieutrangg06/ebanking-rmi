package server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
    // Giá trị cấu hình mặc định (XAMPP: root / không mật khẩu)
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/ebanking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    public static String getUrl() {
        String prop = System.getProperty("db.url");
        if (prop != null && !prop.trim().isEmpty()) return prop.trim();
        String env = System.getenv("DB_URL");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        return DEFAULT_URL;
    }

    public static String getUser() {
        String prop = System.getProperty("db.user");
        if (prop != null && !prop.trim().isEmpty()) return prop.trim();
        String env = System.getenv("DB_USER");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        return DEFAULT_USER;
    }

    public static String getPassword() {
        String prop = System.getProperty("db.password");
        if (prop != null) return prop;
        String env = System.getenv("DB_PASSWORD");
        if (env != null) return env;
        return DEFAULT_PASSWORD;
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Chưa tìm thấy MySQL Driver trong pom.xml!");
            e.printStackTrace();
        }
        return DriverManager.getConnection(getUrl(), getUser(), getPassword());
    }

    /**
     * Tự động khởi tạo Database 'ebanking_db' và các bảng mẫu nếu chưa tồn tại
     * Giúp người dùng hoặc giảng viên chạy Server ngay mà không cần import phpMyAdmin thủ công.
     */
    public static void initializeDatabaseIfPossible() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception ignored) {}

        // 1. Tạo database nếu chưa có
        String rootUrl = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        try (Connection rootConn = DriverManager.getConnection(rootUrl, getUser(), getPassword());
             Statement stmt = rootConn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `ebanking_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (Exception ignored) {
            // MySQL có thể đang tắt hoặc dùng cấu hình từ xa, tiếp tục thử kết nối trực tiếp
        }

        // 2. Tạo các bảng và nạp dữ liệu mẫu
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `accounts` (" +
                    "`id` int(11) NOT NULL AUTO_INCREMENT, " +
                    "`account_number` varchar(20) NOT NULL UNIQUE, " +
                    "`username` varchar(50) NOT NULL UNIQUE, " +
                    "`password` varchar(255) NOT NULL, " +
                    "`full_name` varchar(100) NOT NULL, " +
                    "`balance` double NOT NULL DEFAULT 0, " +
                    "`status` varchar(20) DEFAULT 'ACTIVE', " +
                    "PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `transactions` (" +
                    "`id` int(11) NOT NULL AUTO_INCREMENT, " +
                    "`transaction_type` varchar(30) NOT NULL, " +
                    "`from_account` varchar(20) DEFAULT NULL, " +
                    "`to_account` varchar(20) DEFAULT NULL, " +
                    "`amount` double NOT NULL, " +
                    "`description` text DEFAULT NULL, " +
                    "`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `bills` (" +
                    "`id` int(11) NOT NULL AUTO_INCREMENT, " +
                    "`bill_code` varchar(50) NOT NULL UNIQUE, " +
                    "`service_type` varchar(50) NOT NULL, " +
                    "`customer_name` varchar(100) NOT NULL, " +
                    "`amount` double NOT NULL, " +
                    "`status` varchar(20) DEFAULT 'UNPAID', " +
                    "PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `savings` (" +
                    "`id` int(11) NOT NULL AUTO_INCREMENT, " +
                    "`account_number` varchar(20) NOT NULL, " +
                    "`deposit_amount` double NOT NULL, " +
                    "`interest_rate` double NOT NULL, " +
                    "`term_period` int(11) NOT NULL, " +
                    "`accumulated_interest` double NOT NULL DEFAULT 0, " +
                    "`status` varchar(20) DEFAULT 'ACTIVE', " +
                    "`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // Kiểm tra và khởi tạo dữ liệu mẫu nếu bảng accounts trống
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `accounts`")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate("INSERT INTO `accounts` (`account_number`, `username`, `password`, `full_name`, `balance`, `status`) VALUES " +
                            "('1001', 'usera', '123456', 'Nguyen Van A', 3500000, 'ACTIVE'), " +
                            "('1002', 'userb', '123456', 'Tran Thi B', 1650000, 'LOCKED'), " +
                            "('1003', 'userc', '123456', 'Le Van C', 2000000, 'ACTIVE'), " +
                            "('9999', 'admin', 'admin123', 'System Administrator', 0, 'ACTIVE')");
                    System.out.println(">> [DATABASE] Da nap du lieu mau tai khoan accounts mac dinh.");
                }
            }

            // Dữ liệu mẫu hóa đơn nếu trống
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `bills`")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate("INSERT INTO `bills` (`bill_code`, `service_type`, `customer_name`, `amount`, `status`) VALUES " +
                            "('EVN_HANOI_01', 'Tien Dien', 'Nguyen Van A', 350000, 'UNPAID'), " +
                            "('WA_DANANG_02', 'Tien Nuoc', 'Tran Thi B', 120000, 'UNPAID'), " +
                            "('FPT_NET_03', 'Internet FPT', 'Le Van C', 250000, 'UNPAID')");
                    System.out.println(">> [DATABASE] Da nap du lieu mau hoa don bills mac dinh.");
                }
            }

            // Dữ liệu mẫu sổ tiết kiệm nếu trống
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `savings`")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate("INSERT INTO `savings` (`account_number`, `deposit_amount`, `interest_rate`, `term_period`, `accumulated_interest`, `status`) VALUES " +
                            "('1001', 500000, 6.5, 30, 2500, 'ACTIVE'), " +
                            "('1003', 1000000, 7.0, 60, 15000, 'ACTIVE')");
                    System.out.println(">> [DATABASE] Da nap du lieu mau so tiet kiem savings mac dinh.");
                }
            }

            System.out.println(">> [DATABASE] Dong bo cau truc bang va du lieu ebanking_db hoan tat.");

        } catch (SQLException e) {
            // Không ngắt hệ thống nếu MySQL chưa sẵn sàng (để log cảnh báo)
            System.err.println(">> [DATABASE AUTO-INIT NOTICE] " + e.getMessage());
        }
    }
}
