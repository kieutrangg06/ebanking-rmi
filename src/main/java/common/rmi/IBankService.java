package common.rmi;

import common.models.Account;
import common.models.Bill;
import common.models.Transaction;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.List;

public interface IBankService extends Remote {

    // ==========================================
    // NGƯỜI 1: AUTHENTICATION & CORE SESSION
    // ==========================================
    Account login(String username, String password, IClientCallback callback) throws RemoteException;
    boolean register(String username, String password, String fullName, String accountNumber) throws RemoteException;
    void logout(String username) throws RemoteException;
    double getBalance(String accountNumber) throws RemoteException;
    Account getAccountByNumber(String accountNumber) throws RemoteException;
    List<String> getOnlineUsers() throws RemoteException;

    // ==========================================
    // NGƯỜI 1: REAL-TIME TRANSFER & CALLBACK
    // ==========================================
    /**
     * Chuyển tiền giữa 2 tài khoản, xử lý ACID Transaction và gọi Callback cho người nhận
     */
    boolean transfer(String fromAccountNumber, String toAccountNumber, double amount, String description) throws RemoteException;

    // ==========================================
    // NGƯỜI 2: HÓA ĐƠN & SAO KÊ GIAO DỊCH
    // ==========================================
    /**
     * Tra cứu hóa đơn theo mã (EVN_HANOI_01, WA_DANANG_02, ...)
     */
    Bill queryBill(String billCode) throws RemoteException;

    /**
     * Thanh toán hóa đơn (trừ tiền tài khoản, gạch nợ hóa đơn, ghi lịch sử giao dịch)
     */
    boolean payBill(String accountNumber, String billCode) throws RemoteException;

    /**
     * Lấy toàn bộ lịch sử biến động số dư của 1 tài khoản
     */
    List<Transaction> getTransactionHistory(String accountNumber) throws RemoteException;

    /**
     * Lọc lịch sử biến động số dư của tài khoản theo khoảng ngày (fromDate -> toDate)
     */
    List<Transaction> getTransactionHistoryFiltered(String accountNumber, Date fromDate, Date toDate) throws RemoteException;
}
