package common.rmi;

import common.models.Account;
import common.models.Bill;
import common.models.Saving;
import common.models.Transaction;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IBankService extends Remote {

    // ==========================================
    // PHẦN VIỆC CỦA NGƯỜI 1: AUTH & CHUYỂN KHOẢN
    // ==========================================
    Account login(String username, String password, IClientCallback callback) throws RemoteException;
    boolean register(String username, String password, String fullName, String accountNumber) throws RemoteException;
    void logout(String username) throws RemoteException;
    double getBalance(String accountNumber) throws RemoteException;
    
    /**
     * Chuyển tiền giữa 2 tài khoản, xử lý ACID Transaction và gọi Callback cho người nhận
     */
    boolean transfer(String fromAccountNumber, String toAccountNumber, double amount, String description) throws RemoteException;


    // ==========================================
    // PHẦN VIỆC CỦA NGƯỜI 2: HÓA ĐƠN & SAO KÊ
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


    // ==========================================
    // PHẦN VIỆC CỦA NGƯỜI 3: TIẾT KIỆM & QUẢN TRỊ ADMIN
    // ==========================================
    /**
     * Mở sổ tiết kiệm trực tuyến
     */
    boolean openSaving(String accountNumber, double amount, double interestRate, int termSeconds) throws RemoteException;

    /**
     * Tất toán sổ tiết kiệm (hoàn gốc + lãi về tài khoản chính)
     */
    boolean settleSaving(int savingId) throws RemoteException;

    /**
     * Lấy danh sách sổ tiết kiệm của một tài khoản
     */
    List<Saving> getSavingsByAccount(String accountNumber) throws RemoteException;

    /**
     * Lấy danh sách các tài khoản đang online (dành cho Admin Dashboard)
     */
    List<String> getOnlineUsers() throws RemoteException;

    /**
     * Admin khóa tài khoản và gọi Callback đá văng client ngay lập tức
     */
    boolean lockAccount(String accountNumber, String reason) throws RemoteException;
}