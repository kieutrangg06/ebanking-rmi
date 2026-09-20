package client.callback;

import common.rmi.IClientCallback;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class ClientCallbackImpl extends UnicastRemoteObject implements IClientCallback {
    private static final long serialVersionUID = 1L;
    private final Runnable onBalanceChangedListener;

    // Callback nhận callback listener để cập nhật giao diện Swing
    public ClientCallbackImpl(Runnable onBalanceChangedListener) throws RemoteException {
        super();
        this.onBalanceChangedListener = onBalanceChangedListener;
    }

    @Override
    public void notifyBalanceChange(String message, double newBalance) throws RemoteException {
        // Swing yêu cầu cập nhật UI trên Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            if (onBalanceChangedListener != null) {
                onBalanceChangedListener.run();
            }
            JOptionPane.showMessageDialog(null,
                    "🔔 [BIẾN ĐỘNG SỐ DƯ]\n" + message + "\nSố dư mới: " + String.format("%,.0f VNĐ", newBalance),
                    "Thông Báo Ngân Hàng",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void forceLogout(String reason) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null,
                    "⚠️ BẠN ĐÃ BỊ ĐĂNG XUẤT CƯỠNG CHẾ!\n" + reason,
                    "Cảnh Báo Từ Admin",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        });
    }
}