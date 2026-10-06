package common;

import common.models.Account;
import common.models.Bill;
import common.models.Transaction;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

public class SerializationTest {

    @Test
    public void testBillSerialization() throws Exception {
        Bill bill = new Bill(1, "EVN_HANOI_01", "Tien Dien", "Nguyen Van A", 350000, "UNPAID");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(bill);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object obj = ois.readObject();
            assertTrue(obj instanceof Bill);
            Bill deserialized = (Bill) obj;
            assertEquals("EVN_HANOI_01", deserialized.getBillCode());
            assertEquals("Tien Dien", deserialized.getServiceType());
            assertEquals(350000, deserialized.getAmount());
            assertEquals("UNPAID", deserialized.getStatus());
        }
    }

    @Test
    public void testTransactionSerialization() throws Exception {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Transaction tx = new Transaction(1, "THANH_TOAN_HOA_DON", "1001", "EVN_HANOI_01", 350000, "Thanh toan tien dien", now, "THÀNH CÔNG");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(tx);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object obj = ois.readObject();
            assertTrue(obj instanceof Transaction);
            Transaction deserialized = (Transaction) obj;
            assertEquals("1001", deserialized.getFromAccount());
            assertEquals("EVN_HANOI_01", deserialized.getToAccount());
            assertEquals(350000, deserialized.getAmount());
            assertEquals("THÀNH CÔNG", deserialized.getStatus());
        }
    }

    @Test
    public void testAccountSerialization() throws Exception {
        Account acc = new Account(1, "1001", "usera", "123456", "Nguyen Van A", 3500000, "ACTIVE");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(acc);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object obj = ois.readObject();
            assertTrue(obj instanceof Account);
            Account deserialized = (Account) obj;
            assertEquals("1001", deserialized.getAccountNumber());
            assertEquals(3500000, deserialized.getBalance());
        }
    }
}
