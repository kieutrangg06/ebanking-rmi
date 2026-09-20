package client;

import client.callback.ClientCallbackImpl;
import client.view.DashboardForm;
import common.models.Account;
import common.rmi.IBankService;

import javax.swing.*;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame loginFrame = new JFrame("Đăng Nhập e-Banking");
            loginFrame.setSize(380, 230);
            loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            loginFrame.setLocationRelativeTo(null);

            JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

            JTextField txtIp = new JTextField("localhost");
            JTextField txtUser = new JTextField("usera"); // Mặc định usera
            JPasswordField txtPass = new JPasswordField("123456");
            JButton btnLogin = new JButton("Đăng Nhập");

            panel.add(new JLabel("Server IP:")); panel.add(txtIp);
            panel.add(new JLabel("Tên đăng nhập:")); panel.add(txtUser);
            panel.add(new JLabel("Mật khẩu:")); panel.add(txtPass);
            panel.add(new JLabel("")); panel.add(btnLogin);

            loginFrame.add(panel);
            loginFrame.setVisible(true);

            btnLogin.addActionListener(e -> {
                try {
                    String ip = txtIp.getText().trim();
                    String user = txtUser.getText().trim();
                    String pass = new String(txtPass.getPassword()).trim();

                    // 1. Kết nối Registry qua mạng
                    Registry registry = LocateRegistry.getRegistry(ip, 1099);
                    IBankService bankService = (IBankService) registry.lookup("BankService");

                    // 2. Khởi tạo Dashboard trước để chuẩn bị listener cập nhật số dư
                    DashboardForm[] dashboardHolder = new DashboardForm[1];

                    // 3. Khởi tạo Callback Object truyền lên Server
                    ClientCallbackImpl callback = new ClientCallbackImpl(() -> {
                        if (dashboardHolder[0] != null) {
                            dashboardHolder[0].updateBalanceLabel();
                        }
                    });

                    // 4. Đăng nhập và nộp Callback
                    Account acc = bankService.login(user, pass, callback);
                    if (acc != null) {
                        loginFrame.dispose();
                        dashboardHolder[0] = new DashboardForm(bankService, acc);
                        dashboardHolder[0].setVisible(true);
                    } else {
                        JOptionPane.showMessageDialog(loginFrame, "Sai thông tin hoặc tài khoản đang bị KHÓA!", "Lỗi Đăng Nhập", JOptionPane.ERROR_MESSAGE);
                    }

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(loginFrame, "Không thể kết nối đến Server RMI: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            });
        });
    }
}