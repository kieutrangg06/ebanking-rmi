package common.rmi;

import common.models.Account;

import java.rmi.Remote;
import java.rmi.RemoteException;
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
}
