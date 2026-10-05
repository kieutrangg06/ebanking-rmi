package core;

import common.models.Account;
import common.rmi.IBankService;
import common.rmi.IClientCallback;
import server.db.DatabaseConnection;
import server.service.BankServiceImpl;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoreAuthTransferTest {

    private static IBankService bankService;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("      BẮT ĐẦU CHẠY BỘ KIỂM THỬ CORE AUTH & TRANSFER (DEV 1)      ");
        System.out.println("=================================================================");

        int passed = 0;
        int failed = 0;

        try {
            bankService = new BankServiceImpl();
            resetDatabaseTestData();

            if (runTest("TEST 1: Chống Double-Click Transfer", CoreAuthTransferTest::test1_DoubleClickTransfer)) passed++; else failed++;
            if (runTest("TEST 2: Hai luồng chuyển tiền đồng thời (Concurrency / Race Condition)", CoreAuthTransferTest::test2_ConcurrentTransfers)) passed++; else failed++;
            if (runTest("TEST 3: Insufficient Balance (Không đủ số dư)", CoreAuthTransferTest::test3_InsufficientBalance)) passed++; else failed++;
            if (runTest("TEST 4: Transfer tới tài khoản không tồn tại", CoreAuthTransferTest::test4_TransferToNonExistentAccount)) passed++; else failed++;
            if (runTest("TEST 5: Transfer số tiền <= 0", CoreAuthTransferTest::test5_TransferAmountZeroOrNegative)) passed++; else failed++;
            if (runTest("TEST 6: Receiver Online -> Nhận Callback", CoreAuthTransferTest::test6_ReceiverOnlineCallback)) passed++; else failed++;
            if (runTest("TEST 7: Receiver Offline -> Không gọi Callback", CoreAuthTransferTest::test7_ReceiverOfflineNoCallback)) passed++; else failed++;
            if (runTest("TEST 8: Dead Callback Reference -> Server bắt RemoteException & dọn session", CoreAuthTransferTest::test8_DeadCallbackReferenceCleanup)) passed++; else failed++;
            if (runTest("TEST 9: Giao dịch chuyển cho tài khoản bị khóa -> Rollback toàn vẹn", CoreAuthTransferTest::test9_LockedAccountRollback)) passed++; else failed++;
            if (runTest("TEST 10: Login & Logout -> Quản lý phiên Session sạch sẽ", CoreAuthTransferTest::test10_LogoutSessionCleanup)) passed++; else failed++;
            if (runTest("TEST 11: Register tài khoản mới & Đăng nhập thành công", CoreAuthTransferTest::test11_RegisterAndLogin)) passed++; else failed++;
            if (runTest("TEST 12: Đăng nhập trùng -> Đá phiên cũ (ForceLogout)", CoreAuthTransferTest::test12_ConcurrentDuplicateLogin)) passed++; else failed++;

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng khi chạy bộ kiểm thử: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=================================================================");
        System.out.println("                   KẾT QUẢ KIỂM THỬ CUỐI CÙNG                    ");
        System.out.println("=================================================================");
        System.out.println("Tổng số test cases: 12");
        System.out.println("PASS : " + passed);
        System.out.println("FAIL : " + failed);
        System.out.println("TRẠNG THÁI: " + (failed == 0 ? "HOÀN TOÀN ĐẠT CHUẨN (ALL PASS)" : "CÓ TEST THẤT BẠI"));
        System.out.println("=================================================================\n");

        System.exit(failed == 0 ? 0 : 1);
    }

    private static boolean runTest(String testName, TestCase test) {
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("Đang chạy: " + testName + "...");
        try {
            resetDatabaseTestData();
            boolean ok = test.run();
            if (ok) {
                System.out.println("-> [PASS] " + testName);
                return true;
            } else {
                System.err.println("-> [FAIL] " + testName);
                return false;
            }
        } catch (Throwable t) {
            System.err.println("-> [ERROR] " + testName + " ngoại lệ: " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }

    @FunctionalInterface
    interface TestCase {
        boolean run() throws Exception;
    }

    /**
     * Chuẩn bị dữ liệu mẫu trong DB:
     * 1001 (usera): balance 1,000,000 | ACTIVE
     * 1002 (userb): balance 1,000,000 | LOCKED
     * 1003 (userc): balance 1,000,000 | ACTIVE
     */
    private static void resetDatabaseTestData() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(true);
            try (PreparedStatement ps = conn.prepareStatement("UPDATE accounts SET balance = 1000000, status = 'ACTIVE' WHERE account_number IN ('1001', '1003')")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE accounts SET balance = 1000000, status = 'LOCKED' WHERE account_number = '1002'")) {
                ps.executeUpdate();
            }
        }
    }

    // Mock Callback cho Client
    static class MockClientCallback extends UnicastRemoteObject implements IClientCallback {
        final AtomicBoolean notified = new AtomicBoolean(false);
        final AtomicBoolean forceLoggedOut = new AtomicBoolean(false);
        String lastMessage = "";
        double lastBalance = 0.0;

        public MockClientCallback() throws RemoteException {
            super();
        }

        @Override
        public void notifyBalanceChange(String message, double newBalance) throws RemoteException {
            notified.set(true);
            lastMessage = message;
            lastBalance = newBalance;
        }

        @Override
        public void forceLogout(String reason) throws RemoteException {
            forceLoggedOut.set(true);
        }
    }

    // Mock Dead Callback ném RemoteException giả lập client đứt mạng đột ngột
    static class DeadClientCallback extends UnicastRemoteObject implements IClientCallback {
        public DeadClientCallback() throws RemoteException {
            super();
        }

        @Override
        public void notifyBalanceChange(String message, double newBalance) throws RemoteException {
            throw new RemoteException("Network connection lost (Connection reset by peer)");
        }

        @Override
        public void forceLogout(String reason) throws RemoteException {
            throw new RemoteException("Network connection lost");
        }
    }

    // TEST 1: Double-click transfer
    private static boolean test1_DoubleClickTransfer() throws Exception {
        boolean tx1 = bankService.transfer("1001", "1003", 600000, "Double click 1");
        boolean tx2 = bankService.transfer("1001", "1003", 600000, "Double click 2");

        double bal1001 = bankService.getBalance("1001");
        double bal1003 = bankService.getBalance("1003");

        System.out.println("Tx1: " + tx1 + " | Tx2: " + tx2);
        System.out.println("Số dư 1001: " + bal1001 + " | Số dư 1003: " + bal1003);

        return (tx1 && !tx2) && bal1001 == 400000 && bal1003 == 1600000;
    }

    // TEST 2: Hai luồng chuyển tiền đồng thời (Race condition)
    private static boolean test2_ConcurrentTransfers() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<Boolean> f1 = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return bankService.transfer("1001", "1003", 500000, "Thread 1");
        });

        Future<Boolean> f2 = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return bankService.transfer("1001", "1003", 600000, "Thread 2");
        });

        readyLatch.await();
        startLatch.countDown(); // Kích hoạt 2 luồng cùng lúc

        boolean res1 = f1.get();
        boolean res2 = f2.get();
        executor.shutdown();

        double bal1001 = bankService.getBalance("1001");
        double bal1003 = bankService.getBalance("1003");

        System.out.println("Kết quả luồng 1 (500k): " + res1 + " | Luồng 2 (600k): " + res2);
        System.out.println("Số dư cuối của 1001: " + bal1001 + " | Số dư của 1003: " + bal1003);

        boolean exactlyOneSuccess = (res1 ^ res2);
        boolean balanceNonNegative = bal1001 >= 0 && (bal1001 == 500000 || bal1001 == 400000);
        return exactlyOneSuccess && balanceNonNegative;
    }

    // TEST 3: Insufficient balance
    private static boolean test3_InsufficientBalance() throws Exception {
        boolean ok = bankService.transfer("1001", "1003", 2000000, "Over balance transfer");
        double bal1001 = bankService.getBalance("1001");
        System.out.println("Kết quả chuyển 2tr khi có 1tr: " + ok + " | Số dư: " + bal1001);
        return !ok && bal1001 == 1000000;
    }

    // TEST 4: Transfer tới account không tồn tại
    private static boolean test4_TransferToNonExistentAccount() throws Exception {
        boolean ok = bankService.transfer("1001", "99999999", 100000, "To non-existent");
        double bal1001 = bankService.getBalance("1001");
        System.out.println("Kết quả chuyển tới TK ảo 99999999: " + ok + " | Số dư: " + bal1001);
        return !ok && bal1001 == 1000000;
    }

    // TEST 5: Transfer amount <= 0
    private static boolean test5_TransferAmountZeroOrNegative() throws Exception {
        boolean zeroOk = bankService.transfer("1001", "1003", 0, "Chuyen 0 dong");
        boolean negOk = bankService.transfer("1001", "1003", -50000, "Chuyen am");
        double bal1001 = bankService.getBalance("1001");
        System.out.println("Chuyển 0đ: " + zeroOk + " | Chuyển âm: " + negOk);
        return !zeroOk && !negOk && bal1001 == 1000000;
    }

    // TEST 6: Receiver online -> callback
    private static boolean test6_ReceiverOnlineCallback() throws Exception {
        MockClientCallback cb = new MockClientCallback();
        Account accC = bankService.login("userc", "123456", cb);
        if (accC == null) return false;

        boolean ok = bankService.transfer("1001", "1003", 250000, "Nap tien an trua");
        System.out.println("Transfer result: " + ok + " | Callback triggered: " + cb.notified.get());
        System.out.println("Callback Message: " + cb.lastMessage + " | New Balance: " + cb.lastBalance);

        bankService.logout("userc");
        return ok && cb.notified.get() && cb.lastBalance == 1250000;
    }

    // TEST 7: Receiver offline -> không callback
    private static boolean test7_ReceiverOfflineNoCallback() throws Exception {
        bankService.logout("userc");
        MockClientCallback cb = new MockClientCallback();

        boolean ok = bankService.transfer("1001", "1003", 100000, "Offline transfer");
        double bal1003 = bankService.getBalance("1003");

        System.out.println("Transfer ok: " + ok + " | Callback called: " + cb.notified.get());
        return ok && !cb.notified.get() && bal1003 == 1100000;
    }

    // TEST 8: Receiver tắt app đột ngột -> Dead reference -> remove session, server không crash
    private static boolean test8_DeadCallbackReferenceCleanup() throws Exception {
        DeadClientCallback deadCb = new DeadClientCallback();
        bankService.login("userc", "123456", deadCb);

        List<String> onlinesBefore = bankService.getOnlineUsers();
        System.out.println("Online users trước khi đứt kết nối: " + onlinesBefore);

        boolean ok = bankService.transfer("1001", "1003", 50000, "Dead reference test");

        List<String> onlinesAfter = bankService.getOnlineUsers();
        System.out.println("Online users sau khi Server xử lý dead reference: " + onlinesAfter);

        return ok && !onlinesAfter.contains("1003");
    }

    // TEST 9: Database failure / Locked account -> Rollback toàn vẹn
    private static boolean test9_LockedAccountRollback() throws Exception {
        double balBefore1001 = bankService.getBalance("1001");
        double balBefore1002 = bankService.getBalance("1002");

        boolean ok = bankService.transfer("1001", "1002", 200000, "Chuyen vao tk khoa");

        double balAfter1001 = bankService.getBalance("1001");
        double balAfter1002 = bankService.getBalance("1002");

        System.out.println("Giao dịch tới TK bị khóa: " + ok);
        System.out.println("1001: " + balBefore1001 + " -> " + balAfter1001);
        System.out.println("1002: " + balBefore1002 + " -> " + balAfter1002);

        return !ok && balBefore1001 == balAfter1001 && balBefore1002 == balAfter1002;
    }

    // TEST 10: Logout -> session được remove
    private static boolean test10_LogoutSessionCleanup() throws Exception {
        MockClientCallback cb = new MockClientCallback();
        bankService.login("usera", "123456", cb);

        List<String> onlines1 = bankService.getOnlineUsers();
        System.out.println("Online sau login usera: " + onlines1);
        boolean onlineAfterLogin = onlines1.contains("1001");

        bankService.logout("usera");

        List<String> onlines2 = bankService.getOnlineUsers();
        System.out.println("Online sau logout usera: " + onlines2);
        boolean removedAfterLogout = !onlines2.contains("1001");

        return onlineAfterLogin && removedAfterLogout;
    }

    // TEST 11: Register tài khoản mới & Đăng nhập
    private static boolean test11_RegisterAndLogin() throws Exception {
        String testUser = "testuser_" + (System.currentTimeMillis() % 100000);
        String testAcc = "ACC" + (System.currentTimeMillis() % 100000);

        boolean regOk = bankService.register(testUser, "secret123", "Người Dùng Mới", testAcc);
        System.out.println("Đăng ký tài khoản: " + regOk);

        Account acc = bankService.login(testUser, "secret123", null);
        System.out.println("Đăng nhập với tài khoản vừa tạo: " + (acc != null ? acc.getFullName() : "null"));

        if (acc != null) {
            bankService.logout(testUser);
        }

        return regOk && acc != null && testAcc.equals(acc.getAccountNumber());
    }

    // TEST 12: Đăng nhập trùng -> Đá phiên cũ (ForceLogout)
    private static boolean test12_ConcurrentDuplicateLogin() throws Exception {
        MockClientCallback cb1 = new MockClientCallback();
        MockClientCallback cb2 = new MockClientCallback();

        // Client 1 đăng nhập
        Account acc1 = bankService.login("usera", "123456", cb1);
        boolean cb1Online = acc1 != null && !cb1.forceLoggedOut.get();

        // Client 2 đăng nhập cùng tài khoản usera từ máy khác
        Account acc2 = bankService.login("usera", "123456", cb2);
        boolean cb2Online = acc2 != null && !cb2.forceLoggedOut.get();

        // Client 1 phải nhận thông báo forceLogout
        boolean cb1Kicked = cb1.forceLoggedOut.get();

        System.out.println("Client 1 login: " + cb1Online + " | Client 2 login: " + cb2Online + " | Client 1 bị forceLogout: " + cb1Kicked);

        bankService.logout("usera");
        return cb1Online && cb2Online && cb1Kicked;
    }
}
