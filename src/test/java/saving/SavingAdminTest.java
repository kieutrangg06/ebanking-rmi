package saving;

import common.models.Account;
import common.models.Saving;
import common.rmi.IBankService;
import common.rmi.IClientCallback;
import server.db.DatabaseConnection;
import server.service.BankServiceImpl;
import server.service.InterestCalculatorTask;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ kiểm thử tự động toàn diện cho Dev 3 (Tiết Kiệm & Quản Trị Admin).
 * Kiểm tra các tính năng lõi:
 * 1. Mở sổ tiết kiệm & Khấu trừ số dư an toàn
 * 2. Tiến trình ngầm tính lãi tự động & Callback Event-driven Notification
 * 3. Chống rò rỉ luồng (Thread Leak): Không tính lãi cho sổ đã CLOSED
 * 4. Chống xung đột khóa (Deadlock/Race Condition) giữa luồng tính lãi và luồng tất toán
 * 5. Giám sát phiên kết nối mạng từ xa (Network Session Monitoring)
 * 6. Lệnh điều khiển cưỡng chế từ xa: Admin Kick (forceLogout)
 * 7. Lệnh điều khiển cưỡng chế từ xa: Admin Lock Account & Khóa đăng nhập
 * 8. Admin Unlock Account & Khôi phục đăng nhập
 * 9. Tổng tiền toàn hệ thống (Total System Balance)
 */
public class SavingAdminTest {

    private static BankServiceImpl bankService;

    // Callback giả lập cho Client
    public static class TestClientCallback extends UnicastRemoteObject implements IClientCallback {
        private static final long serialVersionUID = 1L;
        public final AtomicBoolean balanceNotified = new AtomicBoolean(false);
        public final AtomicBoolean forceLoggedOut = new AtomicBoolean(false);
        public volatile String lastBalanceMessage = "";
        public volatile double lastBalance = -1;
        public volatile String lastLogoutReason = "";

        public TestClientCallback() throws RemoteException {
            super();
        }

        @Override
        public void notifyBalanceChange(String message, double newBalance) throws RemoteException {
            this.balanceNotified.set(true);
            this.lastBalanceMessage = message;
            this.lastBalance = newBalance;
        }

        @Override
        public void forceLogout(String reason) throws RemoteException {
            this.forceLoggedOut.set(true);
            this.lastLogoutReason = reason;
        }
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("         BẮT ĐẦU CHẠY KIỂM THỬ TỰ ĐỘNG - PHÂN HỆ DEV 3 (TIẾT KIỆM & ADMIN)");
        System.out.println("================================================================================");

        int passed = 0;
        int failed = 0;

        try {
            // Chuẩn bị dữ liệu mẫu trong DB
            setupDatabase();

            bankService = new BankServiceImpl();

            // Tạm dừng timer nền mặc định để kiểm soát chu kỳ tính lãi chính xác trong test
            InterestCalculatorTask task = bankService.getInterestCalculatorTask();
            task.stop();

            // TEST 1
            if (runTest("TEST 1: Mở sổ tiết kiệm trực tuyến & Khấu trừ số dư", SavingAdminTest::test1_OpenSaving)) passed++; else failed++;

            // TEST 2
            if (runTest("TEST 2: Tiến trình nền tính lãi tự động & Callback báo về Client", SavingAdminTest::test2_InterestCalculationAndCallback)) passed++; else failed++;

            // TEST 3
            if (runTest("TEST 3: Tất toán sổ tiết kiệm (Gốc + Lãi hoàn về ví chính)", SavingAdminTest::test3_SettleSaving)) passed++; else failed++;

            // TEST 4
            if (runTest("TEST 4: Chống rò rỉ luồng (Thread Leak) - Không cộng lãi vào sổ đã CLOSED", SavingAdminTest::test4_NoInterestOnClosedSaving)) passed++; else failed++;

            // TEST 5
            if (runTest("TEST 5: Đa luồng cạnh tranh (Concurrency) giữa tính lãi và tất toán", SavingAdminTest::test5_ConcurrentInterestAndSettle)) passed++; else failed++;

            // TEST 6
            if (runTest("TEST 6: Giám sát phiên Online (Network Session Monitoring)", SavingAdminTest::test6_AdminGetOnlineUsers)) passed++; else failed++;

            // TEST 7
            if (runTest("TEST 7: Admin Kick phiên Online (Remote Revocation - forceLogout)", SavingAdminTest::test7_AdminKickUser)) passed++; else failed++;

            // TEST 8
            if (runTest("TEST 8: Admin Khóa tài khoản (Emergency Freeze & Chặn đăng nhập)", SavingAdminTest::test8_AdminLockAccount)) passed++; else failed++;

            // TEST 9
            if (runTest("TEST 9: Admin Mở khóa tài khoản (Unlock Account & Cho phép đăng nhập lại)", SavingAdminTest::test9_AdminUnlockAccount)) passed++; else failed++;

            // TEST 10
            if (runTest("TEST 10: Thống kê tổng tiền hệ thống & Danh sách tài khoản", SavingAdminTest::test10_SystemStatsAndAccounts)) passed++; else failed++;

        } catch (Exception e) {
            System.err.println("Khởi tạo kiểm thử thất bại: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n================================================================================");
        System.out.printf(" KẾT QUẢ KIỂM THỬ DEV 3: [ TỔNG: %d | THÀNH CÔNG: %d | THẤT BẠI: %d ]\n", (passed + failed), passed, failed);
        System.out.println("================================================================================");

        if (failed == 0) {
            System.out.println(">>> TẤT CẢ 10/10 TEST CASES ĐÃ ĐẠT CHUẨN YÊU CẦU DEV 3 (TIẾT KIỆM & ADMIN)! <<<");
            System.exit(0);
        } else {
            System.err.println(">>> CÓ TEST CASE BỊ LỖI! VUI LÒNG KIỂM TRA LẠI. <<<");
            System.exit(1);
        }
    }

    private static boolean runTest(String testName, Callable<Boolean> testFunc) {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(">> Đang chạy: " + testName);
        try {
            boolean result = testFunc.call();
            if (result) {
                System.out.println(" [PASS] " + testName);
                return true;
            } else {
                System.err.println(" [FAIL] " + testName);
                return false;
            }
        } catch (Throwable t) {
            System.err.println(" [ERROR] " + testName + " - Ngoại lệ: " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }

    private static void setupDatabase() throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Đặt lại số dư chuẩn cho tài khoản test 1001 và 1003
            stmt.executeUpdate("UPDATE accounts SET balance = 5000000, status = 'ACTIVE' WHERE account_number = '1001'");
            stmt.executeUpdate("UPDATE accounts SET balance = 2000000, status = 'ACTIVE' WHERE account_number = '1003'");
            // Đóng các sổ cũ của 1001
            stmt.executeUpdate("UPDATE savings SET status = 'CLOSED' WHERE account_number = '1001'");
        }
    }

    // -------------------------------------------------------------------------
    // TEST 1: Mở sổ tiết kiệm trực tuyến
    // -------------------------------------------------------------------------
    private static boolean test1_OpenSaving() throws Exception {
        double balBefore = bankService.getBalance("1001");
        double depositAmount = 1000000;
        double rate = 5.0;
        int term = 15;

        boolean success = bankService.openSaving("1001", depositAmount, rate, term);
        double balAfter = bankService.getBalance("1001");

        List<Saving> savings = bankService.getSavingsByAccount("1001");
        boolean hasActiveSaving = savings.stream().anyMatch(s -> "ACTIVE".equals(s.getStatus()) && s.getDepositAmount() == depositAmount);

        System.out.printf("Số dư trước: %,.0f VNĐ | Sau: %,.0f VNĐ (Đã trừ: %,.0f VNĐ)\n", balBefore, balAfter, (balBefore - balAfter));
        System.out.println("Mở sổ thành công: " + success + " | Bản ghi ACTIVE tồn tại trong DB: " + hasActiveSaving);

        return success && (balBefore - balAfter == depositAmount) && hasActiveSaving;
    }

    // -------------------------------------------------------------------------
    // TEST 2: Tiến trình nền tính lãi tự động & Callback
    // -------------------------------------------------------------------------
    private static boolean test2_InterestCalculationAndCallback() throws Exception {
        TestClientCallback callback = new TestClientCallback();
        Account acc = bankService.login("usera", "123456", callback);
        if (acc == null) return false;

        List<Saving> beforeList = bankService.getSavingsByAccount("1001");
        Saving activeSaving = beforeList.stream().filter(s -> "ACTIVE".equals(s.getStatus())).findFirst().orElse(null);
        if (activeSaving == null) return false;

        double interestBefore = activeSaving.getAccumulatedInterest();

        // Kích hoạt một chu kỳ tính lãi ngầm
        int processed = bankService.getInterestCalculatorTask().calculateInterest();

        List<Saving> afterList = bankService.getSavingsByAccount("1001");
        Saving updatedSaving = afterList.stream().filter(s -> s.getId() == activeSaving.getId()).findFirst().orElse(null);
        if (updatedSaving == null) return false;

        double interestAfter = updatedSaving.getAccumulatedInterest();
        double expectedAdded = activeSaving.getDepositAmount() * (activeSaving.getInterestRate() / 100.0);

        System.out.printf("Số sổ được xử lý: %d | Lãi trước: %,.0f VNĐ | Lãi sau: %,.0f VNĐ (Tăng: %,.0f VNĐ)\n",
                processed, interestBefore, interestAfter, (interestAfter - interestBefore));
        System.out.println("Client nhận được Callback: " + callback.balanceNotified.get() + " | Nội dung: " + callback.lastBalanceMessage);

        bankService.logout("usera");
        return processed > 0 && (interestAfter - interestBefore == expectedAdded) && callback.balanceNotified.get();
    }

    // -------------------------------------------------------------------------
    // TEST 3: Tất toán sổ tiết kiệm
    // -------------------------------------------------------------------------
    private static boolean test3_SettleSaving() throws Exception {
        List<Saving> savings = bankService.getSavingsByAccount("1001");
        Saving activeSaving = savings.stream().filter(s -> "ACTIVE".equals(s.getStatus())).findFirst().orElse(null);
        if (activeSaving == null) return false;

        double balBefore = bankService.getBalance("1001");
        double totalExpectedRefund = activeSaving.getDepositAmount() + activeSaving.getAccumulatedInterest();

        boolean ok = bankService.settleSaving(activeSaving.getId());
        double balAfter = bankService.getBalance("1001");

        List<Saving> savingsAfter = bankService.getSavingsByAccount("1001");
        Saving closedSaving = savingsAfter.stream().filter(s -> s.getId() == activeSaving.getId()).findFirst().orElse(null);

        System.out.printf("Tất toán Sổ #%d: %b | Hoàn trả: %,.0f VNĐ (Gốc: %,.0f + Lãi: %,.0f)\n",
                activeSaving.getId(), ok, totalExpectedRefund, activeSaving.getDepositAmount(), activeSaving.getAccumulatedInterest());
        System.out.printf("Số dư trước: %,.0f VNĐ | Sau: %,.0f VNĐ | Trạng thái sổ: %s\n",
                balBefore, balAfter, (closedSaving != null ? closedSaving.getStatus() : "null"));

        return ok && (balAfter - balBefore == totalExpectedRefund) && (closedSaving != null && "CLOSED".equals(closedSaving.getStatus()));
    }

    // -------------------------------------------------------------------------
    // TEST 4: Chống rò rỉ luồng (Thread Leak) - Không cộng lãi vào sổ CLOSED
    // -------------------------------------------------------------------------
    private static boolean test4_NoInterestOnClosedSaving() throws Exception {
        // Lấy tất cả sổ CLOSED của 1001
        List<Saving> savings = bankService.getSavingsByAccount("1001");
        Saving closedSaving = savings.stream().filter(s -> "CLOSED".equals(s.getStatus())).findFirst().orElse(null);
        if (closedSaving == null) return false;

        double interestBefore = closedSaving.getAccumulatedInterest();

        // Kích hoạt tiến trình tính lãi
        bankService.getInterestCalculatorTask().calculateInterest();

        List<Saving> savingsAfter = bankService.getSavingsByAccount("1001");
        Saving closedSavingAfter = savingsAfter.stream().filter(s -> s.getId() == closedSaving.getId()).findFirst().orElse(null);
        if (closedSavingAfter == null) return false;

        double interestAfter = closedSavingAfter.getAccumulatedInterest();

        System.out.printf("Sổ đã CLOSED #%d: Lãi trước: %,.0f VNĐ | Sau tính lãi: %,.0f VNĐ\n",
                closedSaving.getId(), interestBefore, interestAfter);

        // Lãi tuyệt đối KHÔNG được tăng
        return interestBefore == interestAfter;
    }

    // -------------------------------------------------------------------------
    // TEST 5: Đa luồng cạnh tranh giữa tính lãi và tất toán (Concurrency)
    // -------------------------------------------------------------------------
    private static boolean test5_ConcurrentInterestAndSettle() throws Exception {
        // Mở sổ mới để kiểm thử tranh chấp đa luồng
        bankService.openSaving("1001", 500000, 10.0, 15);
        List<Saving> list = bankService.getSavingsByAccount("1001");
        Saving testSaving = list.stream().filter(s -> "ACTIVE".equals(s.getStatus())).findFirst().orElse(null);
        if (testSaving == null) return false;

        int savingId = testSaving.getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Luồng 1: Liên tục tính lãi
        Callable<Integer> interestJob = () -> {
            int count = 0;
            for (int i = 0; i < 5; i++) {
                count += bankService.getInterestCalculatorTask().calculateInterest();
                Thread.sleep(10);
            }
            return count;
        };

        // Luồng 2: Thực hiện tất toán
        Callable<Boolean> settleJob = () -> {
            Thread.sleep(15);
            return bankService.settleSaving(savingId);
        };

        Future<Integer> fInterest = pool.submit(interestJob);
        Future<Boolean> fSettle = pool.submit(settleJob);

        int interestCount = fInterest.get(5, TimeUnit.SECONDS);
        boolean settleSuccess = fSettle.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        List<Saving> finalList = bankService.getSavingsByAccount("1001");
        Saving finalSaving = finalList.stream().filter(s -> s.getId() == savingId).findFirst().orElse(null);

        System.out.printf("Đa luồng đồng thời: Tính lãi chạy %d lần | Tất toán kết quả: %b | Trạng thái cuối: %s\n",
                interestCount, settleSuccess, (finalSaving != null ? finalSaving.getStatus() : "null"));

        return settleSuccess && (finalSaving != null && "CLOSED".equals(finalSaving.getStatus()));
    }

    // -------------------------------------------------------------------------
    // TEST 6: Giám sát phiên Online (Network Session Monitoring)
    // -------------------------------------------------------------------------
    private static boolean test6_AdminGetOnlineUsers() throws Exception {
        TestClientCallback cbA = new TestClientCallback();
        TestClientCallback cbC = new TestClientCallback();

        bankService.login("usera", "123456", cbA);
        bankService.login("userc", "123456", cbC);

        List<String> onlines = bankService.getOnlineUsers();
        System.out.println("Danh sách online sau login: " + onlines);

        boolean bothOnline = onlines.contains("1001") && onlines.contains("1003");

        bankService.logout("usera");
        List<String> onlinesAfterLogout = bankService.getOnlineUsers();
        System.out.println("Danh sách online sau logout usera: " + onlinesAfterLogout);

        boolean useraRemoved = !onlinesAfterLogout.contains("1001") && onlinesAfterLogout.contains("1003");

        bankService.logout("userc");
        return bothOnline && useraRemoved;
    }

    // -------------------------------------------------------------------------
    // TEST 7: Admin Kick phiên Online (Remote Revocation - forceLogout)
    // -------------------------------------------------------------------------
    private static boolean test7_AdminKickUser() throws Exception {
        TestClientCallback callback = new TestClientCallback();
        Account acc = bankService.login("userc", "123456", callback);
        if (acc == null) return false;

        boolean wasOnline = bankService.getOnlineUsers().contains("1003");

        // Admin ra lệnh KICK với lý do
        String reason = "Phát hiện spam thao tác bất thường";
        boolean kickOk = bankService.kickUser("1003", reason);

        List<String> onlinesAfterKick = bankService.getOnlineUsers();
        boolean removedFromOnline = !onlinesAfterKick.contains("1003");
        boolean clientReceivedForceLogout = callback.forceLoggedOut.get();
        boolean reasonMatches = callback.lastLogoutReason.contains(reason);

        // Kiểm tra trong CSDL tài khoản vẫn là ACTIVE (chỉ đá phiên chứ không khóa)
        Account accInDb = bankService.login("userc", "123456", null);
        boolean stillCanLogin = (accInDb != null);
        if (accInDb != null) bankService.logout("userc");

        System.out.printf("Kick thành công: %b | Client nhận forceLogout: %b | Bị gỡ khỏi online: %b\n",
                kickOk, clientReceivedForceLogout, removedFromOnline);
        System.out.println("Lý do nhận được: " + callback.lastLogoutReason);
        System.out.println("Tài khoản trong DB vẫn có thể login lại: " + stillCanLogin);

        return wasOnline && kickOk && clientReceivedForceLogout && reasonMatches && removedFromOnline && stillCanLogin;
    }

    // -------------------------------------------------------------------------
    // TEST 8: Admin Khóa tài khoản (Emergency Freeze & Chặn đăng nhập)
    // -------------------------------------------------------------------------
    private static boolean test8_AdminLockAccount() throws Exception {
        TestClientCallback callback = new TestClientCallback();
        Account acc = bankService.login("userc", "123456", callback);
        if (acc == null) return false;

        String lockReason = "Nghi vấn rửa tiền";
        boolean lockOk = bankService.lockAccount("1003", lockReason);

        boolean clientKicked = callback.forceLoggedOut.get();
        boolean removedFromOnline = !bankService.getOnlineUsers().contains("1003");

        // Thử đăng nhập lại -> Phải bị từ chối do trạng thái LOCKED
        Account reLogin = bankService.login("userc", "123456", null);
        boolean loginBlocked = (reLogin == null);

        System.out.printf("Khóa tài khoản: %b | Client bị đá văng: %b | Đăng nhập lại bị chặn: %b\n",
                lockOk, clientKicked, loginBlocked);

        return lockOk && clientKicked && removedFromOnline && loginBlocked;
    }

    // -------------------------------------------------------------------------
    // TEST 9: Admin Mở khóa tài khoản (Unlock Account & Cho phép đăng nhập lại)
    // -------------------------------------------------------------------------
    private static boolean test9_AdminUnlockAccount() throws Exception {
        boolean unlockOk = bankService.unlockAccount("1003");

        // Thử đăng nhập lại sau khi mở khóa -> Phải thành công
        Account reLogin = bankService.login("userc", "123456", null);
        boolean loginAllowed = (reLogin != null);

        if (reLogin != null) {
            bankService.logout("userc");
        }

        System.out.printf("Mở khóa tài khoản: %b | Đăng nhập lại thành công: %b\n", unlockOk, loginAllowed);
        return unlockOk && loginAllowed;
    }

    // -------------------------------------------------------------------------
    // TEST 10: Thống kê tổng tiền hệ thống & Danh sách tài khoản
    // -------------------------------------------------------------------------
    private static boolean test10_SystemStatsAndAccounts() throws Exception {
        double totalBalance = bankService.getTotalSystemBalance();
        List<Account> accounts = bankService.getAllAccounts();

        System.out.printf("Tổng tiền toàn hệ thống: %,.0f VNĐ | Tổng số tài khoản: %d\n", totalBalance, accounts.size());
        for (Account a : accounts) {
            System.out.printf("  - STK: %s | Chủ: %s | Số dư: %,.0f VNĐ | TT: %s\n",
                    a.getAccountNumber(), a.getFullName(), a.getBalance(), a.getStatus());
        }

        boolean hasBalance = totalBalance > 0;
        boolean hasAccounts = accounts.size() >= 3;
        // Kiểm tra mật khẩu bị ẩn an toàn
        boolean passwordHidden = accounts.stream().allMatch(a -> a.getPassword() == null || a.getPassword().isEmpty());

        return hasBalance && hasAccounts && passwordHidden;
    }
}
