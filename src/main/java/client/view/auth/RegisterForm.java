package client.view.auth;

import client.view.ui.ModernButton;
import client.view.ui.ModernPasswordField;
import client.view.ui.ModernTextField;
import client.view.ui.RoundedPanel;
import client.view.ui.UITheme;
import common.rmi.IBankService;
import common.rmi.RmiConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RegisterForm extends JFrame {
    private final LoginForm parentLogin;
    private ModernTextField txtHost;
    private ModernTextField txtAccountNum;
    private ModernTextField txtFullName;
    private ModernTextField txtUsername;
    private ModernPasswordField txtPassword;
    private ModernPasswordField txtConfirmPassword;
    private ModernButton btnRegister;
    private ModernButton btnCancel;
    private JLabel lblStatus;

    public RegisterForm(LoginForm parentLogin, String defaultHost) {
        this.parentLogin = parentLogin;
        initComponents(defaultHost);
    }

    private void initComponents(String defaultHost) {
        setTitle("e-Banking RMI - Mở Tài Khoản Trực Tuyến");
        setSize(500, 680);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parentLogin);
        setResizable(false);
        getContentPane().setBackground(UITheme.BG_MAIN);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBackground(UITheme.BG_MAIN);
        contentPane.setBorder(new EmptyBorder(16, 24, 16, 24));

        RoundedPanel mainCard = new RoundedPanel(20, Color.WHITE);
        mainCard.setLayout(new BoxLayout(mainCard, BoxLayout.Y_AXIS));
        mainCard.setBorder(new EmptyBorder(18, 22, 18, 22));
        mainCard.setShowShadow(true);

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        headerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblTitle = new JLabel("Đăng Ký Tài Khoản Mới");
        lblTitle.setFont(UITheme.FONT_TITLE_XL);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(lblTitle);

        headerPanel.add(Box.createVerticalStrut(4));

        JLabel lblSub = new JLabel("Khởi tạo tài khoản ngân hàng số e-Banking định danh");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(lblSub);

        mainCard.add(headerPanel);
        mainCard.add(Box.createVerticalStrut(14));

        // Form fields
        JPanel formFields = new JPanel();
        formFields.setLayout(new BoxLayout(formFields, BoxLayout.Y_AXIS));
        formFields.setOpaque(false);

        // Host Input
        formFields.add(createFieldLabel("Địa chỉ máy chủ RMI:*"));
        txtHost = new ModernTextField("Địa chỉ máy chủ (VD: 192.168.1.15 hoặc localhost)");
        txtHost.setText(defaultHost != null && !defaultHost.isEmpty() ? defaultHost : RmiConfig.getBestLocalIp());
        txtHost.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtHost);

        formFields.add(Box.createVerticalStrut(8));

        // Account Number Input
        formFields.add(createFieldLabel("Số tài khoản mong muốn (STK):*"));
        txtAccountNum = new ModernTextField("VD: 1004, 8888, 6789...");
        txtAccountNum.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtAccountNum);

        formFields.add(Box.createVerticalStrut(8));

        // Full Name Input
        formFields.add(createFieldLabel("Họ và tên chủ tài khoản:*"));
        txtFullName = new ModernTextField("VD: NGUYEN VAN AN");
        txtFullName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtFullName);

        formFields.add(Box.createVerticalStrut(8));

        // Username Input
        formFields.add(createFieldLabel("Tên đăng nhập hệ thống:*"));
        txtUsername = new ModernTextField("VD: nguyenan2026");
        txtUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtUsername);

        formFields.add(Box.createVerticalStrut(8));

        // Password Input
        formFields.add(createFieldLabel("Mật khẩu bảo mật:*"));
        txtPassword = new ModernPasswordField("Nhập mật khẩu...");
        txtPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtPassword);

        formFields.add(Box.createVerticalStrut(8));

        // Confirm Password Input
        formFields.add(createFieldLabel("Xác nhận lại mật khẩu:*"));
        txtConfirmPassword = new ModernPasswordField("Nhập lại mật khẩu...");
        txtConfirmPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtConfirmPassword);

        formFields.add(Box.createVerticalStrut(6));

        // Status banner
        lblStatus = new JLabel(" ");
        lblStatus.setFont(UITheme.FONT_SMALL);
        lblStatus.setForeground(UITheme.DANGER);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        formFields.add(lblStatus);

        mainCard.add(formFields);
        mainCard.add(Box.createVerticalStrut(10));

        // Buttons
        JPanel actionPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        actionPanel.setOpaque(false);
        actionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        btnCancel = new ModernButton("Hủy Bỏ", ModernButton.Style.SECONDARY);
        btnCancel.setFont(UITheme.FONT_BODY);
        btnCancel.addActionListener(e -> dispose());

        btnRegister = new ModernButton("ĐĂNG KÝ NGAY", ModernButton.Style.PRIMARY);
        btnRegister.setFont(UITheme.FONT_BODY_BOLD);
        btnRegister.addActionListener(e -> performRegister());

        actionPanel.add(btnCancel);
        actionPanel.add(btnRegister);
        mainCard.add(actionPanel);

        contentPane.add(mainCard, BorderLayout.CENTER);
        add(contentPane);

        getRootPane().setDefaultButton(btnRegister);
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL_BOLD);
        label.setForeground(UITheme.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 2, 3, 0));
        return label;
    }

    private void performRegister() {
        String hostRaw = txtHost.getText().trim();
        String accNum = txtAccountNum.getText().trim();
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        String confirmPass = new String(txtConfirmPassword.getPassword()).trim();

        if (hostRaw.isEmpty() || accNum.isEmpty() || fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            lblStatus.setForeground(UITheme.DANGER);
            lblStatus.setText("Vui lòng điền đầy đủ tất cả các trường có dấu (*)!");
            return;
        }

        if (!password.equals(confirmPass)) {
            lblStatus.setForeground(UITheme.DANGER);
            lblStatus.setText("Mật khẩu xác nhận không khớp! Vui lòng kiểm tra lại.");
            txtConfirmPassword.requestFocus();
            return;
        }

        String host = hostRaw;
        int port = RmiConfig.DEFAULT_REGISTRY_PORT;
        if (hostRaw.contains(":")) {
            String[] parts = hostRaw.split(":");
            host = parts[0].trim();
            try { port = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
        }

        final String finalHost = host;
        final int finalPort = port;

        lblStatus.setForeground(UITheme.PRIMARY);
        lblStatus.setText("Đang gửi yêu cầu khởi tạo tài khoản lên máy chủ...");
        btnRegister.setEnabled(false);
        btnCancel.setEnabled(false);

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private String error = null;

            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    Registry registry = LocateRegistry.getRegistry(finalHost, finalPort);
                    IBankService bankService = (IBankService) registry.lookup(RmiConfig.SERVICE_NAME);
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
                                "CHÚC MỪNG BẠN ĐÃ MỞ TÀI KHOẢN THÀNH CÔNG!\n\n" +
                                "• Chủ tài khoản: " + fullName.toUpperCase() + "\n" +
                                "• Số tài khoản: " + accNum + "\n" +
                                "• Tên đăng nhập: " + username + "\n" +
                                "• Số dư khởi tạo: 0 VNĐ\n\n" +
                                "Bạn có thể sử dụng thông tin này để đăng nhập ngay bây giờ.",
                                "Mở Tài Khoản Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        if (parentLogin != null) {
                            parentLogin.setPrefilledUsername(username);
                        }
                        dispose();
                    } else {
                        lblStatus.setForeground(UITheme.DANGER);
                        if (error != null) {
                            lblStatus.setText("Lỗi mạng: Không thể kết nối tới máy chủ RMI!");
                            JOptionPane.showMessageDialog(RegisterForm.this,
                                    "Lỗi kết nối máy chủ RMI:\n" + error,
                                    "Lỗi Đăng Ký", JOptionPane.ERROR_MESSAGE);
                        } else {
                            lblStatus.setText("Tên đăng nhập hoặc số tài khoản đã tồn tại trong hệ thống!");
                            JOptionPane.showMessageDialog(RegisterForm.this,
                                    "Số tài khoản (" + accNum + ") hoặc Tên đăng nhập (" + username + ") đã được đăng ký trước đó.\nVui lòng chọn thông tin khác!",
                                    "Trùng Lặp Dữ Liệu", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    lblStatus.setForeground(UITheme.DANGER);
                    lblStatus.setText("Lỗi hệ thống khi khởi tạo tài khoản!");
                    ex.printStackTrace();
                }
            }
        };

        worker.execute();
    }
}
