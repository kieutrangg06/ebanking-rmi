package common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IClientCallback extends Remote {
    /**
     * Nhận thông báo biến động số dư khi có người chuyển tiền hoặc nhận lãi
     */
    void notifyBalanceChange(String message, double newBalance) throws RemoteException;

    /**
     * Lệnh cưỡng chế từ Admin (đá văng khỏi hệ thống hoặc khóa tài khoản từ xa)
     */
    void forceLogout(String reason) throws RemoteException;
}