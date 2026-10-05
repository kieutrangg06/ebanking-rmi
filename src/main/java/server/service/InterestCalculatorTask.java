package server.service;

import common.models.Saving;
import server.dao.SavingDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Tiến trình nền đa luồng (Multi-threading Scheduled Worker) tự động tính lãi cho các sổ tiết kiệm.
 * Chống rò rỉ luồng (Thread Leak) và chống xung đột khóa (Deadlock/Race Condition) khi tất toán.
 */
public class InterestCalculatorTask implements Runnable {

    private final SavingDAO savingDAO;
    private final BiConsumer<String, String> interestNotificationCallback;
    private ScheduledExecutorService scheduler;
    private volatile boolean isRunning = false;

    /**
     * @param savingDAO DAO truy xuất dữ liệu sổ tiết kiệm
     * @param interestNotificationCallback Callback để bắn thông báo (accountNumber, message) về client
     */
    public InterestCalculatorTask(SavingDAO savingDAO, BiConsumer<String, String> interestNotificationCallback) {
        this.savingDAO = savingDAO;
        this.interestNotificationCallback = interestNotificationCallback;
    }

    /**
     * Khởi động tiến trình ngầm chạy định kỳ
     */
    public synchronized void start(long initialDelaySeconds, long periodSeconds) {
        if (isRunning) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "InterestCalculator-Worker");
            t.setDaemon(true); // Daemon thread để không cản trở JVM shutdown
            return t;
        });
        scheduler.scheduleAtFixedRate(this, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
        isRunning = true;
        System.out.println(">> [THREAD NỀN] Tiến trình tự động tính lãi đã khởi chạy (Chu kỳ: " + periodSeconds + "s)...");
    }

    /**
     * Dừng tiến trình ngầm an toàn
     */
    public synchronized void stop() {
        if (!isRunning) return;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        isRunning = false;
        System.out.println(">> [THREAD NỀN] Đã dừng tiến trình tự động tính lãi.");
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Phương thức thực thi trong mỗi chu kỳ quét
     */
    @Override
    public void run() {
        try {
            calculateInterest();
        } catch (Throwable t) {
            System.err.println("Lỗi ngoài dự kiến trong InterestCalculatorTask: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Quét và tính lãi cho các sổ tiết kiệm đang ACTIVE.
     * Có thể gọi thủ công trực tiếp từ Unit Test.
     */
    public synchronized int calculateInterest() {
        int processedCount = 0;
        try {
            // 1. Chỉ lấy các sổ có status = 'ACTIVE' (Chống lỗi Thread Leak tính lãi vào sổ đã đóng)
            List<Saving> activeSavings = savingDAO.getAllActiveSavings();

            for (Saving saving : activeSavings) {
                int id = saving.getId();
                String accNum = saving.getAccountNumber();
                double deposit = saving.getDepositAmount();
                double rate = saving.getInterestRate();

                // 2. Tính tiền lãi theo chu kỳ: lãi = Tiền gửi * (Lãi suất / 100)
                double addedInterest = deposit * (rate / 100.0);
                if (addedInterest <= 0) continue;

                // 3. Cập nhật vào DB với điều kiện status = 'ACTIVE' để chống Race Condition nếu khách vừa bấm tất toán
                int updatedRows = savingDAO.addInterest(id, addedInterest);

                if (updatedRows > 0) {
                    processedCount++;
                    // 4. Bắn Callback Event-driven Notification về cho Client nếu đang Online
                    if (interestNotificationCallback != null) {
                        String msg = "Tiền lãi từ Sổ tiết kiệm #" + id + " vừa phát sinh +" +
                                String.format("%,.0f VNĐ", addedInterest) + " (Lãi suất: " + rate + "%/chu kỳ)";
                        try {
                            interestNotificationCallback.accept(accNum, msg);
                        } catch (Exception ex) {
                            System.err.println("Lỗi khi bắn callback lãi suất cho " + accNum + ": " + ex.getMessage());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn cơ sở dữ liệu khi tính lãi tiết kiệm: " + e.getMessage());
            e.printStackTrace();
        }
        return processedCount;
    }
}
