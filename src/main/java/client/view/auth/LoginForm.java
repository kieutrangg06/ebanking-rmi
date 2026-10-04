package client.view.auth;

import client.callback.ClientCallbackImpl;
import client.view.DashboardForm;
import common.models.Account;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class LoginForm extends JFrame {
    private JTextField txtHost;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnLogin;
    private JButton btnRegister;
    private JLabel lblStatus;

    public LoginForm() {
        initComponents();
    }

    private void initComponents() {
        setTitle("e-Banking RMI - Đăng Nhập Hệ Thống");
        setSize(420, 360);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 25, 20, 25));
        mainPanel.setBackground(new Color(245, 247, 250));

        // Header
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        headerPanel.setBackground(new Color(245, 247, 250));
        JLabel lblTitle = new JLabel("e-Banking RMI", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(new Color(24, 76, 120));

        JLabel lblSub = new JLabel("Hệ Thống Ngân Hàng Trực Tuyến Phân Tán", SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(Color.GRAY);

        headerPanel.add(lblTitle);
        headerPanel.add(lblSub);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Form Fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(245, 247, 250));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Server IP
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        formPanel.add(new JLabel("Máy chủ RMI:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        txtHost = new JTextField("localhost");
        formPanel.add(txtHost, gbc);

        // Username
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        formPanel.add(new JLabel("Tên đăng nhập:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.7;
        txtUsername = new JTextField("usera");
        formPanel.add(txtUsername, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        formPanel.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.7;
        txtPassword = new JPasswordField("123456");
        formPanel.add(txtPassword, gbc);

        // Status Label
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        lblStatus = new JLabel(" ", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(Color.RED);
        formPanel.add(lblStatus, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.setBackground(new Color(245, 247, 250));

        btnRegister = new JButton("Đăng Ký");
        btnRegister.setBackground(new Color(230, 235, 245));
        btnRegister.setFocusPainted(false);

        btnLogin = new JButton("Đăng Nhập");
        btnLogin.setBackground(new Color(24, 119, 242));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogin.setFocusPainted(false);

        buttonPanel.add(btnRegister);
        buttonPanel.add(btnLogin);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Enter key to submit
        getRootPane().setDefaultButton(btnLogin);

        // Events
        btnLogin.addActionListener(e -> performLogin());
        btnRegister.addActionListener(e -> openRegisterForm());
    }

    private void performLogin() {
        String host = txtHost.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            lblStatus.setText("Vui lòng điền đầy đủ máy chủ, username và mật khẩu!");
            return;
        }

        lblStatus.setForeground(Color.BLUE);
        lblStatus.setText("Đang kết nối máy chủ RMI...");
        btnLogin.setEnabled(false);
        btnRegister.setEnabled(false);

        SwingWorker<Account, Void> worker = new SwingWorker<>() {
            private IBankService bankService;
            private String errorMsg = null;
            private DashboardForm[] dashboardHolder = new DashboardForm[1];

            @Override
            protected Account doInBackground() throws Exception {
                try {
                    Registry registry = LocateRegistry.getRegistry(host, 1099);
                    bankService = (IBankService) registry.lookup("BankService");

                    // Tạo callback cho client
                    ClientCallbackImpl callback = new ClientCallbackImpl(() -> {
                        if (dashboardHolder[0] != null) {
                            dashboardHolder[0].updateBalanceLabel();
                        }
                    });

                    return bankService.login(username, password, callback);
                } catch (Exception ex) {
                    errorMsg = ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                btnLogin.setEnabled(true);
                btnRegister.setEnabled(true);
                try {
                    Account acc = get();
                    if (acc != null) {
                        lblStatus.setForeground(new Color(0, 150, 0));
                        lblStatus.setText("Đăng nhập thành công!");
                        dispose();

                        dashboardHolder[0] = new DashboardForm(bankService, acc);
                        dashboardHolder[0].setVisible(true);
                    } else {
                        lblStatus.setForeground(Color.RED);
                        if (errorMsg != null) {
                            lblStatus.setText("Lỗi mạng: Không kết nối được RMI Server!");
                            JOptionPane.showMessageDialog(LoginForm.this,
                                    "Không thể kết nối đến máy chủ RMI: " + errorMsg,
                                    "Lỗi Kết Nối RMI", JOptionPane.ERROR_MESSAGE);
                        } else {
                            lblStatus.setText("Sai tài khoản, mật khẩu hoặc tài khoản đã bị khóa!");
                            JOptionPane.showMessageDialog(LoginForm.this,
                                    "Sai tên đăng nhập, mật khẩu hoặc tài khoản của bạn đang bị KHÓA!",
                                    "Đăng Nhập Thất Bại", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    lblStatus.setForeground(Color.RED);
                    lblStatus.setText("Lỗi xử lý đăng nhập!");
                    ex.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    private void openRegisterForm() {
        String host = txtHost.getText().trim();
        RegisterForm regForm = new RegisterForm(this, host);
        regForm.setVisible(true);
    }

    public void setPrefilledUsername(String username) {
        txtUsername.setText(username);
        txtPassword.setText("");
        txtPassword.requestFocus();
    }
}
