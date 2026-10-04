package client;

import client.view.auth.LoginForm;

import javax.swing.*;

public class ClientMain {
    public static void main(String[] args) {
        // Thiết lập Look & Feel hệ điều hành để giao diện đẹp hơn
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Khởi động màn hình Đăng Nhập
        SwingUtilities.invokeLater(() -> {
            LoginForm loginForm = new LoginForm();
            loginForm.setVisible(true);
        });
    }
}
