package client.view.auth;

import client.callback.ClientCallbackImpl;
import client.view.DashboardForm;
import client.view.ui.ModernButton;
import client.view.ui.ModernPasswordField;
import client.view.ui.ModernTextField;
import client.view.ui.RoundedPanel;
import client.view.ui.UITheme;
import common.models.Account;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class LoginForm extends JFrame {
    private ModernTextField txtHost;
    private ModernTextField txtUsername;
    private ModernPasswordField txtPassword;
    private ModernButton btnLogin;
    private ModernButton btnRegister;
    private JButton btnTogglePwd;
    private JLabel lblStatus;
    private JLabel lblServerBadge;
    private boolean pwdVisible = false;

    public LoginForm() {
        initComponents();
    }

    private void initComponents() {
        setTitle("e-Banking RMI - Cổng Đăng Nhập Điện Tử");
        setSize(480, 580);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(UITheme.BG_MAIN);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBackground(UITheme.BG_MAIN);
        contentPane.setBorder(new EmptyBorder(25, 30, 25, 30));

        // Card trung tâm bo tròn đổ bóng nhẹ
        RoundedPanel mainCard = new RoundedPanel(20, Color.WHITE);
        mainCard.setLayout(new BoxLayout(mainCard, BoxLayout.Y_AXIS));
        mainCard.setBorder(new EmptyBorder(25, 25, 25, 25));
        mainCard.setShowShadow(true);

        // Header Panel: Logo & Title
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        headerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Biểu tượng ngân hàng vẽ đồ họa trực tiếp
        JPanel logoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = 52;
                int x = (getWidth() - size) / 2;
                int y = 0;
                // Vòng ngoài
                g2.setColor(UITheme.PRIMARY_LIGHT);
                g2.fillOval(x - 4, y - 4, size + 8, size + 8);
                // Vòng trong
                GradientPaint gp = new GradientPaint(x, y, UITheme.PRIMARY, x + size, y + size, UITheme.PRIMARY_HOVER);
                g2.setPaint(gp);
                g2.fillOval(x, y, size, size);

                // Biểu tượng khiên / trụ ngân hàng
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Mái đền ngân hàng
                g2.drawLine(x + 14, y + 20, x + 26, y + 13);
                g2.drawLine(x + 26, y + 13, x + 38, y + 20);
                g2.drawLine(x + 14, y + 20, x + 38, y + 20);
                // Các cột
                g2.drawLine(x + 18, y + 23, x + 18, y + 33);
                g2.drawLine(x + 26, y + 23, x + 26, y + 33);
                g2.drawLine(x + 34, y + 23, x + 34, y + 33);
                // Nền đáy
                g2.drawLine(x + 14, y + 36, x + 38, y + 36);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(200, 60);
            }
        };
        logoPanel.setOpaque(false);
        headerPanel.add(logoPanel);

        JLabel lblTitle = new JLabel("e-Banking RMI");
        lblTitle.setFont(UITheme.FONT_TITLE_XL);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(lblTitle);

        headerPanel.add(Box.createVerticalStrut(4));

        JLabel lblSub = new JLabel("Hệ Thống Ngân Hàng Trực Tuyến Phân Tán");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(lblSub);

        headerPanel.add(Box.createVerticalStrut(8));

        // Badge trạng thái kết nối máy chủ RMI
        lblServerBadge = new JLabel("● Máy chủ RMI: 127.0.0.1:1099");
        lblServerBadge.setFont(UITheme.FONT_SMALL_BOLD);
        lblServerBadge.setForeground(UITheme.SUCCESS_DARK);
        lblServerBadge.setBackground(UITheme.SUCCESS_LIGHT);
        lblServerBadge.setOpaque(true);
        lblServerBadge.setBorder(new EmptyBorder(3, 10, 3, 10));
        lblServerBadge.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(lblServerBadge);

        mainCard.add(headerPanel);
        mainCard.add(Box.createVerticalStrut(20));

        // Form Fields Container
        JPanel formFields = new JPanel();
        formFields.setLayout(new BoxLayout(formFields, BoxLayout.Y_AXIS));
        formFields.setOpaque(false);

        // Host Input
        formFields.add(createFieldLabel("Địa chỉ máy chủ RMI:"));
        txtHost = new ModernTextField("VD: localhost hoặc 192.168.1.10");
        txtHost.setText("localhost");
        txtHost.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        formFields.add(txtHost);

        formFields.add(Box.createVerticalStrut(12));

        // Username Input
        formFields.add(createFieldLabel("Tên đăng nhập:"));
        txtUsername = new ModernTextField("Nhập tên đăng nhập tài khoản...");
        txtUsername.setText("usera");
        txtUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        formFields.add(txtUsername);

        formFields.add(Box.createVerticalStrut(12));

        // Password Input với nút ẩn/hiện mật khẩu
        formFields.add(createFieldLabel("Mật khẩu bảo mật:"));
        JPanel pwdContainer = new JPanel(new BorderLayout(4, 0));
        pwdContainer.setOpaque(false);
        pwdContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        txtPassword = new ModernPasswordField("Nhập mật khẩu...");
        txtPassword.setText("123456");

        btnTogglePwd = new JButton("👁");
        btnTogglePwd.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        btnTogglePwd.setFocusPainted(false);
        btnTogglePwd.setBorderPainted(false);
        btnTogglePwd.setContentAreaFilled(false);
        btnTogglePwd.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnTogglePwd.setToolTipText("Ẩn/Hiện mật khẩu");
        btnTogglePwd.addActionListener(e -> togglePasswordVisibility());

        pwdContainer.add(txtPassword, BorderLayout.CENTER);
        pwdContainer.add(btnTogglePwd, BorderLayout.EAST);
        formFields.add(pwdContainer);

        formFields.add(Box.createVerticalStrut(10));

        // Status banner
        lblStatus = new JLabel(" ");
        lblStatus.setFont(UITheme.FONT_SMALL);
        lblStatus.setForeground(UITheme.DANGER);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        formFields.add(lblStatus);

        mainCard.add(formFields);
        mainCard.add(Box.createVerticalStrut(14));

        // Action Buttons
        JPanel actionPanel = new JPanel(new GridLayout(2, 1, 0, 10));
        actionPanel.setOpaque(false);
        actionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));

        btnLogin = new ModernButton("ĐĂNG NHẬP HỆ THỐNG", ModernButton.Style.PRIMARY);
        btnLogin.setFont(UITheme.FONT_BODY_BOLD);
        btnLogin.addActionListener(e -> performLogin());

        btnRegister = new ModernButton("Mở Tài Khoản Mới (Đăng Ký)", ModernButton.Style.OUTLINE);
        btnRegister.setFont(UITheme.FONT_BODY);
        btnRegister.addActionListener(e -> openRegisterForm());

        actionPanel.add(btnLogin);
        actionPanel.add(btnRegister);
        mainCard.add(actionPanel);

        contentPane.add(mainCard, BorderLayout.CENTER);
        add(contentPane);

        // Enter key to login
        getRootPane().setDefaultButton(btnLogin);
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL_BOLD);
        label.setForeground(UITheme.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 2, 4, 0));
        return label;
    }

    private void togglePasswordVisibility() {
        pwdVisible = !pwdVisible;
        if (pwdVisible) {
            txtPassword.setEchoChar((char) 0);
            btnTogglePwd.setText("🔒");
        } else {
            txtPassword.setEchoChar('•');
            btnTogglePwd.setText("👁");
        }
    }

    private void performLogin() {
        String host = txtHost.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            lblStatus.setForeground(UITheme.DANGER);
            lblStatus.setText("Vui lòng điền đầy đủ máy chủ, username và mật khẩu!");
            return;
        }

        lblStatus.setForeground(UITheme.PRIMARY);
        lblStatus.setText("Đang kết nối và xác thực với máy chủ RMI...");
        btnLogin.setEnabled(false);
        btnRegister.setEnabled(false);

        SwingWorker<Account, Void> worker = new SwingWorker<>() {
            private IBankService bankService;
            private String errorMsg = null;
            private final DashboardForm[] dashboardHolder = new DashboardForm[1];

            @Override
            protected Account doInBackground() throws Exception {
                try {
                    Registry registry = LocateRegistry.getRegistry(host, 1099);
                    bankService = (IBankService) registry.lookup("BankService");

                    // Tạo callback cho client nhận thông báo biến động số dư theo thời gian thực
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
                        lblStatus.setForeground(UITheme.SUCCESS);
                        lblStatus.setText("Đăng nhập thành công!");
                        dispose();

                        dashboardHolder[0] = new DashboardForm(bankService, acc);
                        dashboardHolder[0].setVisible(true);
                    } else {
                        lblStatus.setForeground(UITheme.DANGER);
                        if (errorMsg != null) {
                            lblStatus.setText("Lỗi mạng: Không thể kết nối RMI Server (" + host + ":1099)!");
                            JOptionPane.showMessageDialog(LoginForm.this,
                                    "Không thể kết nối đến máy chủ RMI:\n" + errorMsg +
                                    "\n\nVui lòng đảm bảo ServerMain đã được khởi chạy trên máy chủ.",
                                    "Lỗi Kết Nối RMI", JOptionPane.ERROR_MESSAGE);
                        } else {
                            lblStatus.setText("Sai tên đăng nhập, mật khẩu hoặc tài khoản đang bị KHÓA!");
                            JOptionPane.showMessageDialog(LoginForm.this,
                                    "Sai tên đăng nhập, mật khẩu hoặc tài khoản của bạn đang bị KHÓA bởi Quản trị viên!",
                                    "Đăng Nhập Thất Bại", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    lblStatus.setForeground(UITheme.DANGER);
                    lblStatus.setText("Lỗi hệ thống khi đăng nhập!");
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
        lblStatus.setForeground(UITheme.SUCCESS);
        lblStatus.setText("Đã điền tài khoản vừa tạo. Vui lòng nhập mật khẩu để đăng nhập.");
    }
}
