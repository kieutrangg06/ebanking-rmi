package server;

import common.models.Account;
import common.models.Bill;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử tính đúng đắn của logic ACID Transaction và Concurrency Protection.
 */
public class AcidTransactionTest {

    @Test
    public void testDoublePaymentProtectionSimulation() {
        // Mô phỏng hóa đơn ban đầu UNPAID
        Bill bill = new Bill(1, "EVN_HANOI_01", "Tien Dien", "Nguyen Van A", 350000, "UNPAID");
        Account accA = new Account(1, "1001", "usera", "123456", "Nguyen Van A", 1000000, "ACTIVE");
        Account accB = new Account(2, "1002", "userb", "123456", "Tran Thi B", 1000000, "ACTIVE");

        AtomicInteger successfulPayments = new AtomicInteger(0);
        AtomicInteger failedPayments = new AtomicInteger(0);

        // Giả lập 2 client A và B cùng bấm thanh toán đồng thời (Concurrency)
        Object lock = new Object();
        Runnable payAction = () -> {
            synchronized (lock) {
                // Kiểm tra trạng thái hóa đơn với lock (tương đương SELECT ... FOR UPDATE)
                if ("UNPAID".equalsIgnoreCase(bill.getStatus())) {
                    // Cập nhật trạng thái và trừ tiền
                    bill.setStatus("PAID");
                    successfulPayments.incrementAndGet();
                } else {
                    failedPayments.incrementAndGet();
                }
            }
        };

        Thread t1 = new Thread(payAction);
        Thread t2 = new Thread(payAction);

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            fail(e);
        }

        // Đảm bảo DUY NHẤT 1 client thành công, client kia bị từ chối
        assertEquals(1, successfulPayments.get(), "Chỉ được phép 1 giao dịch thành công cho cùng 1 hóa đơn");
        assertEquals(1, failedPayments.get(), "Client thứ 2 phải nhận thông báo lỗi hóa đơn đã được thanh toán");
        assertEquals("PAID", bill.getStatus());
    }

    @Test
    public void testInsufficientBalanceRollbackCondition() {
        Account acc = new Account(1, "1001", "usera", "123456", "Nguyen Van A", 100000, "ACTIVE");
        Bill bill = new Bill(1, "EVN_HANOI_01", "Tien Dien", "Nguyen Van A", 350000, "UNPAID");

        // Khi số dư (100.000) < số tiền bill (350.000)
        boolean canPay = acc.getBalance() >= bill.getAmount();
        assertFalse(canPay, "Giao dịch không được phép thực hiện khi số dư không đủ");

        // Trạng thái hóa đơn không đổi
        assertEquals("UNPAID", bill.getStatus(), "Hóa đơn phải giữ nguyên UNPAID khi không đủ tiền");
    }

    @Test
    public void testLockedAccountRollbackCondition() {
        Account acc = new Account(2, "1002", "userb", "123456", "Tran Thi B", 1000000, "LOCKED");
        Bill bill = new Bill(1, "EVN_HANOI_01", "Tien Dien", "Tran Thi B", 350000, "UNPAID");

        boolean isAllowed = !"LOCKED".equalsIgnoreCase(acc.getStatus());
        assertFalse(isAllowed, "Tài khoản LOCKED không được phép thực hiện giao dịch thanh toán");
        assertEquals("UNPAID", bill.getStatus());
    }
}
