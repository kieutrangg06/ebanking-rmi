package client.view.auth;

import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RegisterForm extends JFrame {
    private final LoginForm parentLogin;
    private JTextField txtHost;
    private JTextField txtAccountNum;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JPasswordField txtConfirmPassword;
    private JTextField txtFullName;
    private JButton btnRegister;
    private JButton btnCancel;
    private JLabel lblStatus;

    public RegisterForm(LoginForm parentLogin, String defaultHost) {
        this.parentLogin = parentLogin;
        initComponents(defaultHost);
    }

    private void initComponents(String defaultHost) {
        setTitle("e-Banking RMI - Đăng Ký Tài Khoản Mới");
        setSize(440, 460);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parentLogin);
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 25, 15, 25));
        mainPanel.setBackground(new Color(245, 247, 250));

        // Header
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        headerPanel.setBackground(new Color(245, 247, 250));
        JLabel lblTitle = new JLabel("Đăng Ký Tài Khoản", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTitle.setForeground(new Color(24, 76, 120));

        JLabel lblSub = new JLabel("Tạo tài khoản trực tuyến trên hệ thống eBanking", SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(Color.GRAY);

        headerPanel.add(lblTitle);
        headerPanel.add(lblSub);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Form Fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(245, 247, 250));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Host
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.35;
        formPanel.add(new JLabel("Máy chủ RMI:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.65;
        txtHost = new JTextField(defaultHost != null && !defaultHost.isEmpty() ? defaultHost : "localhost");
        formPanel.add(txtHost, gbc);

        // Account Number
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Số tài khoản:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        txtAccountNum = new JTextField();
        formPanel.add(txtAccountNum, gbc);

        // Full Name
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Họ và tên:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        txtFullName = new JTextField();
        formPanel.add(txtFullName, gbc);

        // Username
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Tên đăng nhập:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3;
        txtUsername = new JTextField();
        formPanel.add(txtUsername, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4;
        txtPassword = new JPasswordField();
        formPanel.add(txtPassword, gbc);

        // Confirm Password
        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("Nhập lại MK:"), gbc);
        gbc.gridx = 1; gbc.gridy = 5;
        txtConfirmPassword = new JPasswordField();
        formPanel.add(txtConfirmPassword, gbc);

        // Status Label
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        lblStatus = new JLabel(" ", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(Color.RED);
        formPanel.add(lblStatus, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.setBackground(new Color(245, 247, 250));

        btnCancel = new JButton("Quay Lại");
        btnCancel.setBackground(new Color(230, 235, 245));
        btnCancel.setFocusPainted(false);

        btnRegister = new JButton("Đăng Ký Ngay");
        btnRegister.setBackground(new Color(34, 139, 34));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRegister.setFocusPainted(false);

        buttonPanel.add(btnCancel);
        buttonPanel.add(btnRegister);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Events
        btnCancel.addActionListener(e -> dispose());
        btnRegister.addActionListener(e -> performRegister());
    }

    private void performRegister() {
        String host = txtHost.getText().trim();
        String accNum = txtAccountNum.getText().trim();
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        String confirmPass = new String(txtConfirmPassword.getPassword()).trim();

        if (host.isEmpty() || accNum.isEmpty() || fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            lblStatus.setText("Vui lòng điền đầy đủ tất cả thông tin!");
            return;
        }

        if (!password.equals(confirmPass)) {
            lblStatus.setText("Mật khẩu xác nhận không khớp!");
            return;
        }

        lblStatus.setForeground(Color.BLUE);
        lblStatus.setText("Đang gửi yêu cầu đăng ký lên máy chủ...");
        btnRegister.setEnabled(false);
        btnCancel.setEnabled(false);

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private String error = null;

            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    Registry registry = LocateRegistry.getRegistry(host, 1099);
                    IBankService bankService = (IBankService) registry.lookup("BankService");
                    return bankService.register(username, password, fullName, accNum);
                } catch (Exception ex) {
                    error = ex.getMessage();
                    return false;
                }
            }

            @Override
            protected void done() {
                btnRegister.setEnabled(true);
                btnCancel.setEnabled(true);
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(RegisterForm.this,
                                "Chúc mừng! Đăng ký tài khoản thành công.\nSố tài khoản: " + accNum + "\nTên đăng nhập: " + username,
                                "Đăng Ký Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        if (parentLogin != null) {
                            parentLogin.setPrefilledUsername(username);
                        }
                        dispose();
                    } else {
                        lblStatus.setForeground(Color.RED);
                        if (error != null) {
                            lblStatus.setText("Lỗi mạng: Không kết nối được RMI Server!");
                            JOptionPane.showMessageDialog(RegisterForm.this, "Lỗi kết nối: " + error, "Lỗi", JOptionPane.ERROR_MESSAGE);
                        } else {
                            lblStatus.setText("Tên đăng nhập hoặc Số tài khoản đã tồn tại!");
                            JOptionPane.showMessageDialog(RegisterForm.this,
                                    "Đăng ký thất bại: Tên đăng nhập hoặc Số tài khoản đã được sử dụng!",
                                    "Đăng Ký Thất Bại", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    lblStatus.setForeground(Color.RED);
                    lblStatus.setText("Lỗi xử lý đăng ký!");
                    ex.printStackTrace();
                }
            }
        };

        worker.execute();
    }
}
