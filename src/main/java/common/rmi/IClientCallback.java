package common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

//Remote Interface phục vụ chiều giao tiếp ngược: Server gọi về Client (RMI Callback).
public interface IClientCallback extends Remote {

    void notifyBalanceChange(String message, double newBalance) throws RemoteException;

    void forceLogout(String reason) throws RemoteException;
}