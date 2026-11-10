package core;

import common.models.Account;
import common.rmi.RmiConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DepositWithdrawAndNetworkTest {

    @Test
    public void testRmiConfigFixedPortsAndHostname() {
        assertEquals(1099, RmiConfig.DEFAULT_REGISTRY_PORT, "Cổng Registry phải là 1099 cố định");
        assertEquals(1098, RmiConfig.DEFAULT_SERVER_PORT, "Cổng Server Object phải là 1098 cố định");
        assertEquals(1097, RmiConfig.DEFAULT_CALLBACK_PORT, "Cổng Client Callback phải là 1097 cố định");
        assertEquals("BankService", RmiConfig.SERVICE_NAME);

        RmiConfig.setRmiHostname("192.168.1.99");
        assertEquals("192.168.1.99", RmiConfig.getRmiHostname());

        List<RmiConfig.NetworkInterfaceInfo> interfaces = RmiConfig.getAvailableNetworkInterfaces();
        assertNotNull(interfaces);
        assertFalse(interfaces.isEmpty(), "Máy tính phải có ít nhất 1 interface (kể cả loopback)");

        String bestIp = RmiConfig.getBestLocalIp();
        assertNotNull(bestIp);
        assertFalse(bestIp.isEmpty());
    }

    @Test
    public void testDepositAndWithdrawCalculation() {
        Account acc = new Account(1, "1001", "usera", "123456", "Nguyen Van A", 1000000.0, "ACTIVE");

        // Mô phỏng logic nạp tiền
        double depositAmt = 500000.0;
        acc.setBalance(acc.getBalance() + depositAmt);
        assertEquals(1500000.0, acc.getBalance(), 0.001, "Số dư sau khi nạp phải tăng 500.000 VNĐ");

        // Mô phỏng logic rút tiền hợp lệ
        double withdrawAmt = 300000.0;
        assertTrue(acc.getBalance() >= withdrawAmt, "Số dư phải đủ để rút tiền");
        acc.setBalance(acc.getBalance() - withdrawAmt);
        assertEquals(1200000.0, acc.getBalance(), 0.001, "Số dư sau khi rút phải là 1.200.000 VNĐ");

        // Mô phỏng từ chối rút tiền vượt quá số dư
        double overWithdrawAmt = 2000000.0;
        assertFalse(acc.getBalance() >= overWithdrawAmt, "Không được phép rút quá số dư hiện có");
    }

    @Test
    public void testPortAllocationSafety() {
        int safePort = RmiConfig.allocateSafePort(1097);
        assertTrue(safePort >= 0, "Cổng cấp phát phải hợp lệ (>= 0)");
    }
}
