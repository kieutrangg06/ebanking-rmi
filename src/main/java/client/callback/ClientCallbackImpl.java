package client.callback;

import client.view.auth.LoginForm;
import common.rmi.IClientCallback;
import common.rmi.RmiConfig;

import java.awt.Window;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class ClientCallbackImpl extends UnicastRemoteObject implements IClientCallback {
    private static final long serialVersionUID = 1L;
    private final Runnable onBalanceChangedListener;

    /**
     * Khởi tạo Callback với cổng CỐ ĐỊNH (mặc định 1097) để mở tường lửa.
     * Sử dụng allocateSafePort để tự động fallback nếu trên cùng máy đang có client khác chiếm cổng.
     */
    public ClientCallbackImpl(int preferredPort, Runnable onBalanceChangedListener) throws RemoteException {
        super(RmiConfig.allocateSafePort(preferredPort));
        this.onBalanceChangedListener = onBalanceChangedListener;
    }

    public ClientCallbackImpl(Runnable onBalanceChangedListener) throws RemoteException {
        this(RmiConfig.DEFAULT_CALLBACK_PORT, onBalanceChangedListener);
    }

    @Override
    public void notifyBalanceChange(String message, double newBalance) throws RemoteException {
        // Swing yêu cầu cập nhật UI trên Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            if (onBalanceChangedListener != null) {
                onBalanceChangedListener.run();
            }
            JOptionPane.showMessageDialog(null,
                    "🔔 [BIẾN ĐỘNG SỐ DƯ REALTIME]\n" + message + "\nSố dư hiện tại: " + String.format("%,.0f VNĐ", newBalance),
                    "Thông Báo Ngân Hàng Số",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void forceLogout(String reason) throws RemoteException {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null,
                    "⚠️ BẠN ĐÃ BỊ ĐĂNG XUẤT CƯỠNG CHẾ BỞI QUẢN TRỊ VIÊN!\n\nLý do: " + reason,
                    "Cảnh Báo An Ninh Hệ Thống",
                    JOptionPane.ERROR_MESSAGE);

            // Đóng tất cả cửa sổ giao dịch đang mở và quay về màn hình Đăng Nhập
            for (Window window : Window.getWindows()) {
                window.dispose();
            }
            new LoginForm().setVisible(true);
        });
    }
}
