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
import common.rmi.RmiConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class LoginForm extends JFrame {
    private ModernTextField txtHost;
    private ModernTextField txtPort;
    private ModernTextField txtClientIp;
    private ModernTextField txtCallbackPort;
    private ModernTextField txtUsername;
    private ModernPasswordField txtPassword;
    private ModernButton btnLogin;
    private ModernButton btnRegister;
    private ModernButton btnPing;
    private JButton btnTogglePwd;
    private JButton btnToggleNetwork;
    private JPanel networkConfigPanel;
    private JLabel lblStatus;
    private JLabel lblServerBadge;
    private boolean pwdVisible = false;
    private boolean networkConfigVisible = false;

    public LoginForm() {
        initComponents();
        // Tự động kiểm tra kết nối với server mặc định khi mở app
        SwingUtilities.invokeLater(this::performPing);
    }

    private void initComponents() {
        setTitle("e-Banking RMI - Cổng Đăng Nhập Điện Tử Phân Tán");
        setSize(520, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(UITheme.BG_MAIN);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBackground(UITheme.BG_MAIN);
        contentPane.setBorder(new EmptyBorder(16, 24, 16, 24));

        // Card trung tâm bo tròn đổ bóng nhẹ
        RoundedPanel mainCard = new RoundedPanel(20, Color.WHITE);
        mainCard.setLayout(new BoxLayout(mainCard, BoxLayout.Y_AXIS));
        mainCard.setBorder(new EmptyBorder(18, 22, 18, 22));
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
                int size = 48;
                int x = (getWidth() - size) / 2;
                int y = 0;
                g2.setColor(UITheme.PRIMARY_LIGHT);
                g2.fillOval(x - 3, y - 3, size + 6, size + 6);
                GradientPaint gp = new GradientPaint(x, y, UITheme.PRIMARY, x + size, y + size, UITheme.PRIMARY_HOVER);
                g2.setPaint(gp);
                g2.fillOval(x, y, size, size);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 13, y + 18, x + 24, y + 12);
                g2.drawLine(x + 24, y + 12, x + 35, y + 18);
                g2.drawLine(x + 13, y + 18, x + 35, y + 18);
                g2.drawLine(x + 16, y + 21, x + 16, y + 30);
                g2.drawLine(x + 24, y + 21, x + 24, y + 30);
                g2.drawLine(x + 32, y + 21, x + 32, y + 30);
                g2.drawLine(x + 13, y + 33, x + 35, y + 33);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(200, 52);
            }
        };
        logoPanel.setOpaque(false);
        headerPanel.add(logoPanel);

        JLabel lblTitle = new JLabel("e-Banking RMI");
        lblTitle.setFont(UITheme.FONT_TITLE_XL);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(lblTitle);

        headerPanel.add(Box.createVerticalStrut(2));

        JLabel lblSub = new JLabel("Hệ Thống Ngân Hàng Trực Tuyến Phân Tán (Multi-Node LAN/VPN)");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(lblSub);

        headerPanel.add(Box.createVerticalStrut(6));

        // Badge trạng thái kết nối máy chủ RMI
        lblServerBadge = new JLabel("● Đang kiểm tra kết nối...");
        lblServerBadge.setFont(UITheme.FONT_SMALL_BOLD);
        lblServerBadge.setForeground(UITheme.TEXT_MUTED);
        lblServerBadge.setBackground(UITheme.BG_MAIN);
        lblServerBadge.setOpaque(true);
        lblServerBadge.setBorder(new EmptyBorder(3, 10, 3, 10));
        lblServerBadge.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(lblServerBadge);

        mainCard.add(headerPanel);
        mainCard.add(Box.createVerticalStrut(12));

        // Form Fields Container
        JPanel formFields = new JPanel();
        formFields.setLayout(new BoxLayout(formFields, BoxLayout.Y_AXIS));
        formFields.setOpaque(false);

        // Server IP & Port Row với nút Ping kiểm tra
        JPanel hostPortLabelPanel = new JPanel(new BorderLayout());
        hostPortLabelPanel.setOpaque(false);
        hostPortLabelPanel.add(createFieldLabel("Địa chỉ Máy Chủ RMI Server:"), BorderLayout.WEST);

        btnToggleNetwork = new JButton("⚙️ Mạng LAN/VPN");
        btnToggleNetwork.setFont(UITheme.FONT_SMALL_BOLD);
        btnToggleNetwork.setForeground(UITheme.PRIMARY);
        btnToggleNetwork.setContentAreaFilled(false);
        btnToggleNetwork.setBorderPainted(false);
        btnToggleNetwork.setFocusPainted(false);
        btnToggleNetwork.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnToggleNetwork.addActionListener(e -> toggleNetworkSettings());
        hostPortLabelPanel.add(btnToggleNetwork, BorderLayout.EAST);
        formFields.add(hostPortLabelPanel);

        JPanel hostRow = new JPanel(new BorderLayout(6, 0));
        hostRow.setOpaque(false);
        hostRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        txtHost = new ModernTextField("Server IP (VD: 192.168.1.15 hoặc localhost)");
        txtHost.setText(RmiConfig.getBestLocalIp());

        txtPort = new ModernTextField("1099");
        txtPort.setText(String.valueOf(RmiConfig.DEFAULT_REGISTRY_PORT));
        txtPort.setPreferredSize(new Dimension(65, 36));

        btnPing = new ModernButton("⚡ Ping", ModernButton.Style.SECONDARY);
        btnPing.setFont(UITheme.FONT_SMALL_BOLD);
        btnPing.setCornerRadius(8);
        btnPing.addActionListener(e -> performPing());

        hostRow.add(txtHost, BorderLayout.CENTER);
        hostRow.add(txtPort, BorderLayout.EAST);
        formFields.add(hostRow);

        JPanel pingActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        pingActionPanel.setOpaque(false);
        pingActionPanel.add(btnPing);
        formFields.add(pingActionPanel);

        // Panel Cấu hình Mạng Mở rộng (Collapsible Network Config)
        networkConfigPanel = new JPanel();
        networkConfigPanel.setLayout(new BoxLayout(networkConfigPanel, BoxLayout.Y_AXIS));
        networkConfigPanel.setBackground(new Color(248, 250, 252));
        networkConfigPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));
        networkConfigPanel.setVisible(false);

        JLabel lblNetTitle = new JLabel("🛡️ CẤU HÌNH MÁY KHÁCH (CLIENT RMI CALLBACK):");
        lblNetTitle.setFont(UITheme.FONT_SMALL_BOLD);
        lblNetTitle.setForeground(UITheme.PRIMARY_DARK);
        networkConfigPanel.add(lblNetTitle);
        networkConfigPanel.add(Box.createVerticalStrut(4));

        JLabel lblNetHelp = new JLabel("<html><i>Để Server gọi Callback về Client, máy Client phải cung cấp IP LAN và cổng cố định (không bị chặn tường lửa).</i></html>");
        lblNetHelp.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblNetHelp.setForeground(UITheme.TEXT_MUTED);
        networkConfigPanel.add(lblNetHelp);
        networkConfigPanel.add(Box.createVerticalStrut(6));

        JPanel clientNetRow = new JPanel(new BorderLayout(6, 0));
        clientNetRow.setOpaque(false);
        txtClientIp = new ModernTextField("IP máy khách này");
        txtClientIp.setText(RmiConfig.getBestLocalIp());

        txtCallbackPort = new ModernTextField("1097");
        txtCallbackPort.setText(String.valueOf(RmiConfig.DEFAULT_CALLBACK_PORT));
        txtCallbackPort.setPreferredSize(new Dimension(65, 32));

        clientNetRow.add(txtClientIp, BorderLayout.CENTER);
        clientNetRow.add(txtCallbackPort, BorderLayout.EAST);
        networkConfigPanel.add(clientNetRow);

        formFields.add(networkConfigPanel);
        formFields.add(Box.createVerticalStrut(6));

        // Username Input
        formFields.add(createFieldLabel("Tên đăng nhập:"));
        txtUsername = new ModernTextField("Nhập usera hoặc admin...");
        txtUsername.setText("usera");
        txtUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        formFields.add(txtUsername);

        formFields.add(Box.createVerticalStrut(10));

        // Password Input với nút ẩn/hiện mật khẩu
        formFields.add(createFieldLabel("Mật khẩu bảo mật:"));
        JPanel pwdContainer = new JPanel(new BorderLayout(4, 0));
        pwdContainer.setOpaque(false);
        pwdContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

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

        formFields.add(Box.createVerticalStrut(8));

        // Status banner
        lblStatus = new JLabel(" ");
        lblStatus.setFont(UITheme.FONT_SMALL);
        lblStatus.setForeground(UITheme.DANGER);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        formFields.add(lblStatus);

        mainCard.add(formFields);
        mainCard.add(Box.createVerticalStrut(10));

        // Action Buttons
        JPanel actionPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        actionPanel.setOpaque(false);
        actionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));

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
        label.setBorder(new EmptyBorder(0, 2, 3, 0));
        return label;
    }

    private void toggleNetworkSettings() {
        networkConfigVisible = !networkConfigVisible;
        networkConfigPanel.setVisible(networkConfigVisible);
        btnToggleNetwork.setText(networkConfigVisible ? "🔼 Thu Gọn Mạng" : "⚙️ Mạng LAN/VPN");
        revalidate();
        repaint();
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

    private void performPing() {
        String host = txtHost.getText().trim();
        int port = RmiConfig.DEFAULT_REGISTRY_PORT;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (Exception ignored) {}

        final String targetHost = host.isEmpty() ? "127.0.0.1" : host;
        final int targetPort = port;

        lblServerBadge.setText("● Đang ping " + targetHost + ":" + targetPort + "...");
        lblServerBadge.setForeground(UITheme.TEXT_MUTED);
        lblServerBadge.setBackground(UITheme.BG_MAIN);
        btnPing.setEnabled(false);

        SwingWorker<RmiConfig.PingResult, Void> worker = new SwingWorker<>() {
            @Override
            protected RmiConfig.PingResult doInBackground() {
                return RmiConfig.testRmiService(targetHost, targetPort);
            }

            @Override
            protected void done() {
                btnPing.setEnabled(true);
                try {
                    RmiConfig.PingResult res = get();
                    if (res.success) {
                        lblServerBadge.setText("● Máy chủ RMI: " + targetHost + ":" + targetPort + " (" + res.latencyMs + "ms) - ONLINE");
                        lblServerBadge.setForeground(UITheme.SUCCESS_DARK);
                        lblServerBadge.setBackground(UITheme.SUCCESS_LIGHT);
                        lblStatus.setText(" ");
                    } else {
                        lblServerBadge.setText("● Máy chủ RMI: " + targetHost + ":" + targetPort + " - OFFLINE");
                        lblServerBadge.setForeground(UITheme.DANGER);
                        lblServerBadge.setBackground(new Color(254, 242, 242));
                    }
                } catch (Exception ex) {
                    lblServerBadge.setText("● Lỗi kiểm tra máy chủ");
                    lblServerBadge.setForeground(UITheme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void performLogin() {
        String host = txtHost.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        int registryPort = RmiConfig.DEFAULT_REGISTRY_PORT;
        try {
            registryPort = Integer.parseInt(txtPort.getText().trim());
        } catch (Exception ignored) {}

        String clientIp = (txtClientIp != null && !txtClientIp.getText().trim().isEmpty())
                ? txtClientIp.getText().trim()
                : RmiConfig.getBestLocalIp();

        int callbackPort = RmiConfig.DEFAULT_CALLBACK_PORT;
        if (txtCallbackPort != null) {
            try { callbackPort = Integer.parseInt(txtCallbackPort.getText().trim()); } catch (Exception ignored) {}
        }

        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            lblStatus.setForeground(UITheme.DANGER);
            lblStatus.setText("Vui lòng điền đầy đủ máy chủ, username và mật khẩu!");
            return;
        }

        final String finalHost = host;
        final int finalRegPort = registryPort;
        final String finalClientIp = clientIp;
        final int finalCbPort = callbackPort;

        lblStatus.setForeground(UITheme.PRIMARY);
        lblStatus.setText("Đang kết nối và xác thực với máy chủ RMI (" + finalHost + ":" + finalRegPort + ")...");
        btnLogin.setEnabled(false);
        btnRegister.setEnabled(false);

        SwingWorker<Account, Void> worker = new SwingWorker<>() {
            private IBankService bankService;
            private String errorMsg = null;
            private final DashboardForm[] dashboardHolder = new DashboardForm[1];

            @Override
            protected Account doInBackground() throws Exception {
                try {
                    // CỰC KỲ QUAN TRỌNG: Thiết lập IP máy khách trước khi export Callback
                    // Giúp Server khi gọi ngược lại Callback sẽ biết đúng IP mạng của máy Client
                    RmiConfig.setRmiHostname(finalClientIp);

                    Registry registry = LocateRegistry.getRegistry(finalHost, finalRegPort);
                    bankService = (IBankService) registry.lookup(RmiConfig.SERVICE_NAME);

                    // Tạo callback cho client nhận thông báo biến động số dư theo thời gian thực (Cổng CỐ ĐỊNH)
                    ClientCallbackImpl callback = new ClientCallbackImpl(finalCbPort, () -> {
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

                        // Phân quyền chuyên nghiệp: Admin vào AdminDashboardForm, Khách hàng vào DashboardForm
                        if ("admin".equalsIgnoreCase(acc.getUsername()) || "9999".equals(acc.getAccountNumber())) {
                            new client.view.admin.AdminDashboardForm(bankService, acc).setVisible(true);
                        } else {
                            dashboardHolder[0] = new DashboardForm(bankService, acc);
                            dashboardHolder[0].setVisible(true);
                        }
                    } else {
                        lblStatus.setForeground(UITheme.DANGER);
                        if (errorMsg != null) {
                            lblStatus.setText("Lỗi mạng: Không thể kết nối RMI Server (" + finalHost + ":" + finalRegPort + ")!");
                            JOptionPane.showMessageDialog(LoginForm.this,
                                    "Không thể kết nối đến máy chủ RMI:\n" + errorMsg +
                                    "\n\nNGUYÊN NHÂN THƯỜNG GẶP KHI DEPLOY LIÊN MÁY:\n" +
                                    "1. ServerMain chưa được bật trên máy chủ.\n" +
                                    "2. Tường lửa (Firewall) máy chủ chưa mở cổng TCP " + finalRegPort + " và 1098.\n" +
                                    "3. Hai máy tính chưa cùng dải mạng WiFi/LAN hoặc chưa kết nối VPN.",
                                    "Lỗi Kết Nối RMI Phân Tán", JOptionPane.ERROR_MESSAGE);
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
