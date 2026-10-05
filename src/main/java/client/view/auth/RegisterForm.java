package client.view.auth;

import client.view.ui.ModernButton;
import client.view.ui.ModernPasswordField;
import client.view.ui.ModernTextField;
import client.view.ui.RoundedPanel;
import client.view.ui.UITheme;
import common.rmi.IBankService;

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
        setSize(500, 670);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parentLogin);
        setResizable(false);
        getContentPane().setBackground(UITheme.BG_MAIN);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBackground(UITheme.BG_MAIN);
        contentPane.setBorder(new EmptyBorder(20, 25, 20, 25));

        RoundedPanel mainCard = new RoundedPanel(20, Color.WHITE);
        mainCard.setLayout(new BoxLayout(mainCard, BoxLayout.Y_AXIS));
        mainCard.setBorder(new EmptyBorder(20, 25, 20, 25));
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

        JLabel lblSub = new JLabel("Mở tài khoản thanh toán trực tuyến nhanh chóng & an toàn");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(lblSub);

        mainCard.add(headerPanel);
        mainCard.add(Box.createVerticalStrut(15));

        // Form Fields Container
        JPanel formFields = new JPanel();
        formFields.setLayout(new BoxLayout(formFields, BoxLayout.Y_AXIS));
        formFields.setOpaque(false);

        // RMI Host
        formFields.add(createFieldLabel("Máy chủ RMI:"));
        txtHost = new ModernTextField("localhost");
        txtHost.setText(defaultHost != null && !defaultHost.isEmpty() ? defaultHost : "localhost");
        txtHost.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtHost);
        formFields.add(Box.createVerticalStrut(10));

        // Số tài khoản
        formFields.add(createFieldLabel("Số tài khoản mong muốn (*):"));
        txtAccountNum = new ModernTextField("VD: 1004, 2026, 8888...");
        txtAccountNum.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtAccountNum);
        formFields.add(Box.createVerticalStrut(10));

        // Họ và tên
        formFields.add(createFieldLabel("Họ và tên chủ tài khoản (*):"));
        txtFullName = new ModernTextField("VD: NGUYEN VAN A");
        txtFullName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtFullName);
        formFields.add(Box.createVerticalStrut(10));

        // Username
        formFields.add(createFieldLabel("Tên đăng nhập hệ thống (*):"));
        txtUsername = new ModernTextField("VD: usera, nguyenvan_a");
        txtUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtUsername);
        formFields.add(Box.createVerticalStrut(10));

        // Password
        formFields.add(createFieldLabel("Mật khẩu bảo mật (*):"));
        txtPassword = new ModernPasswordField("Nhập mật khẩu (tối thiểu 6 ký tự)...");
        txtPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtPassword);
        formFields.add(Box.createVerticalStrut(10));

        // Confirm Password
        formFields.add(createFieldLabel("Nhập lại mật khẩu xác nhận (*):"));
        txtConfirmPassword = new ModernPasswordField("Nhập lại mật khẩu giống bên trên...");
        txtConfirmPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtConfirmPassword);
        formFields.add(Box.createVerticalStrut(10));

        // Status banner
        lblStatus = new JLabel(" ");
        lblStatus.setFont(UITheme.FONT_SMALL);
        lblStatus.setForeground(UITheme.DANGER);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        formFields.add(lblStatus);

        mainCard.add(formFields);
        mainCard.add(Box.createVerticalStrut(12));

        // Buttons
        JPanel actionPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        actionPanel.setOpaque(false);
        actionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        btnCancel = new ModernButton("Quay Lại", ModernButton.Style.SECONDARY);
        btnCancel.addActionListener(e -> dispose());

        btnRegister = new ModernButton("ĐĂNG KÝ NGAY", ModernButton.Style.SUCCESS);
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
        String host = txtHost.getText().trim();
        String accNum = txtAccountNum.getText().trim();
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        String confirmPass = new String(txtConfirmPassword.getPassword()).trim();

        if (host.isEmpty() || accNum.isEmpty() || fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
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

        lblStatus.setForeground(UITheme.PRIMARY);
        lblStatus.setText("Đang gửi yêu cầu khởi tạo tài khoản lên máy chủ...");
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
                            lblStatus.setText("Lỗi mạng: Không kết nối được RMI Server!");
                            JOptionPane.showMessageDialog(RegisterForm.this,
                                    "Không thể kết nối đến máy chủ RMI: " + error,
                                    "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                        } else {
                            lblStatus.setText("Tên đăng nhập hoặc Số tài khoản đã được sử dụng!");
                            JOptionPane.showMessageDialog(RegisterForm.this,
                                    "Tên đăng nhập hoặc Số tài khoản đã tồn tại trên hệ thống!\nVui lòng chọn một số tài khoản hoặc username khác.",
                                    "Đăng Ký Không Thành Công", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    lblStatus.setForeground(UITheme.DANGER);
                    lblStatus.setText("Lỗi hệ thống khi đăng ký!");
                    ex.printStackTrace();
                }
            }
        };

        worker.execute();
    }
}
