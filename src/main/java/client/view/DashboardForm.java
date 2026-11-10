package client.view;

import client.view.auth.LoginForm;
import client.view.bill.BillPayForm;
import client.view.bill.TransactionHistoryForm;
import client.view.saving.SavingForm;
import client.view.transfer.TransferForm;
import client.view.ui.ModernButton;
import client.view.ui.ModernTextField;
import client.view.ui.RoundedPanel;
import client.view.ui.TransactionReceiptDialog;
import client.view.ui.UITheme;
import common.models.Account;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;

/**
 * Giao diện Trung Tâm Ngân Hàng Số Dành Cho Khách Hàng (Customer Unified Portal).
 * Thiết kế chuẩn Fintech hiện đại, đồng bộ 100% tất cả dịch vụ:
 * 1. Chuyển Tiền 24/7 (TransferForm)
 * 2. Nạp tiền & Rút tiền tức thì (Deposit & Withdraw Modals)
 * 3. Thanh Toán Hóa Đơn (BillPayForm)
 * 4. Tiết Kiệm Tích Lũy (SavingForm)
 * 5. Nhật Ký & Sao Kê Giao Dịch (TransactionHistoryForm)
 */
public class DashboardForm extends JFrame {

    private final IBankService bankService;
    private final Account currentAccount;

    private JLabel lblBalance;
    private JButton btnToggleBalance;
    private boolean isBalanceHidden = false;

    private TransferForm transferForm;
    private BillPayForm billPayForm;
    private SavingForm savingForm;
    private TransactionHistoryForm historyForm;
    private JTabbedPane tabbedPane;

    public DashboardForm(IBankService bankService, Account account) {
        this.bankService = bankService;
        this.currentAccount = account;

        setTitle("e-Banking RMI - Không Gian Giao Dịch Ngân Hàng Số: " + account.getFullName() + " (STK: " + account.getAccountNumber() + ")");
        setSize(1120, 760);
        setMinimumSize(new Dimension(1000, 680));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        // Bắt sự kiện đóng cửa sổ để dọn dẹp session trên Server an toàn
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                performLogout(true);
            }
        });

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BG_MAIN);

        // =====================================================================
        // 1. THANH ĐIỀU HƯỚNG TRÊN CÙNG (TOP NAVIGATION BAR)
        // =====================================================================
        JPanel topPanel = new JPanel(new BorderLayout(16, 0));
        topPanel.setBackground(Color.WHITE);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));

        // Khối bên trái: Logo ngân hàng, Avatar & Thông tin tài khoản
        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        leftGroup.setOpaque(false);

        // Logo ngân hàng
        JPanel logoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = 38;
                GradientPaint gp = new GradientPaint(0, 0, UITheme.PRIMARY, size, size, UITheme.PRIMARY_HOVER);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, size, size, 10, 10);

                // Biểu tượng ngân hàng
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(9, 14, 19, 9);
                g2.drawLine(19, 9, 29, 14);
                g2.drawLine(9, 14, 29, 14);
                g2.drawLine(12, 17, 12, 25);
                g2.drawLine(19, 17, 19, 25);
                g2.drawLine(26, 17, 26, 25);
                g2.drawLine(9, 28, 29, 28);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(38, 38);
            }
        };
        logoPanel.setOpaque(false);
        leftGroup.add(logoPanel);

        // Avatar tròn viết hoa chữ cái đầu
        JPanel avatarPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UITheme.PRIMARY_LIGHT);
                g2.fillOval(0, 0, 38, 38);
                g2.setColor(UITheme.PRIMARY);
                g2.setFont(UITheme.FONT_TITLE);
                String name = currentAccount.getFullName();
                String initial = (name != null && !name.trim().isEmpty()) ? name.trim().substring(0, 1).toUpperCase() : "U";
                FontMetrics fm = g2.getFontMetrics();
                int x = (38 - fm.stringWidth(initial)) / 2;
                int y = (38 - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initial, x, y);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(38, 38);
            }
        };
        avatarPanel.setOpaque(false);
        leftGroup.add(avatarPanel);

        JPanel userInfoBox = new JPanel(new GridLayout(2, 1, 0, 2));
        userInfoBox.setOpaque(false);

        JLabel lblUser = new JLabel(currentAccount.getFullName().toUpperCase() + "  |  STK: " + currentAccount.getAccountNumber());
        lblUser.setFont(UITheme.FONT_BODY_BOLD);
        lblUser.setForeground(UITheme.TEXT_MAIN);

        JLabel lblType = new JLabel("● Khách hàng trực tuyến (Tài khoản ACTIVE)");
        lblType.setFont(UITheme.FONT_SMALL_BOLD);
        lblType.setForeground(UITheme.SUCCESS_DARK);

        userInfoBox.add(lblUser);
        userInfoBox.add(lblType);
        leftGroup.add(userInfoBox);

        // Khối bên phải: Số dư khả dụng & các thao tác nhanh
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightActions.setOpaque(false);

        // Thẻ số dư khả dụng
        RoundedPanel balanceCard = new RoundedPanel(12, UITheme.SUCCESS_LIGHT);
        balanceCard.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        balanceCard.setBorder(new EmptyBorder(2, 12, 2, 8));
        balanceCard.setShowShadow(false);

        JLabel lblBalTitle = new JLabel("SỐ DƯ:");
        lblBalTitle.setFont(UITheme.FONT_SMALL_BOLD);
        lblBalTitle.setForeground(UITheme.SUCCESS_DARK);

        lblBalance = new JLabel(UITheme.MONEY_FORMAT.format(currentAccount.getBalance()));
        lblBalance.setFont(UITheme.FONT_BODY_BOLD);
        lblBalance.setForeground(UITheme.SUCCESS_DARK);

        btnToggleBalance = new JButton("👁");
        btnToggleBalance.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
        btnToggleBalance.setFocusPainted(false);
        btnToggleBalance.setBorderPainted(false);
        btnToggleBalance.setContentAreaFilled(false);
        btnToggleBalance.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnToggleBalance.setToolTipText("Ẩn/Hiện số dư");
        btnToggleBalance.addActionListener(e -> toggleBalanceVisibility());

        balanceCard.add(lblBalTitle);
        balanceCard.add(lblBalance);
        balanceCard.add(btnToggleBalance);
        rightActions.add(balanceCard);

        // Nút NẠP TIỀN
        ModernButton btnDeposit = new ModernButton("➕ Nạp Tiền", ModernButton.Style.SUCCESS);
        btnDeposit.setFont(UITheme.FONT_SMALL_BOLD);
        btnDeposit.setCornerRadius(8);
        btnDeposit.setMargin(new Insets(6, 12, 6, 12));
        btnDeposit.addActionListener(e -> openDepositDialog());
        rightActions.add(btnDeposit);

        // Nút RÚT TIỀN
        ModernButton btnWithdraw = new ModernButton("➖ Rút Tiền", ModernButton.Style.OUTLINE);
        btnWithdraw.setFont(UITheme.FONT_SMALL_BOLD);
        btnWithdraw.setCornerRadius(8);
        btnWithdraw.setMargin(new Insets(6, 12, 6, 12));
        btnWithdraw.addActionListener(e -> openWithdrawDialog());
        rightActions.add(btnWithdraw);

        // Nút Làm mới
        ModernButton btnRefresh = new ModernButton("🔄 Làm Mới", ModernButton.Style.SECONDARY);
        btnRefresh.setFont(UITheme.FONT_SMALL_BOLD);
        btnRefresh.setCornerRadius(8);
        btnRefresh.setMargin(new Insets(6, 10, 6, 10));
        btnRefresh.addActionListener(e -> updateBalanceLabel());
        rightActions.add(btnRefresh);

        // Nút Đăng xuất
        ModernButton btnLogout = new ModernButton("🚪 Đăng Xuất", ModernButton.Style.DANGER);
        btnLogout.setFont(UITheme.FONT_SMALL_BOLD);
        btnLogout.setCornerRadius(8);
        btnLogout.setMargin(new Insets(6, 12, 6, 12));
        btnLogout.addActionListener(e -> performLogout(false));
        rightActions.add(btnLogout);

        topPanel.add(leftGroup, BorderLayout.WEST);
        topPanel.add(rightActions, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // =====================================================================
        // 2. KHÔNG GIAN DỊCH VỤ NGÂN HÀNG (4 MODULE ĐỒNG BỘ TUYỆT ĐỐI)
        // =====================================================================
        transferForm = new TransferForm(bankService, currentAccount, this::updateBalanceLabel);
        billPayForm = new BillPayForm(bankService, currentAccount, this::updateBalanceLabel);
        savingForm = new SavingForm(bankService, currentAccount, this::updateBalanceLabel);
        historyForm = new TransactionHistoryForm(bankService, currentAccount);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY_BOLD);
        tabbedPane.setBackground(Color.WHITE);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder());

        // 4 Dịch vụ chính
        tabbedPane.addTab("💸 Chuyển Khoản 24/7", transferForm);
        tabbedPane.addTab("🧾 Hóa Đơn & Tiện Ích", billPayForm);
        tabbedPane.addTab("💰 Tiết Kiệm Tích Lũy", savingForm);
        tabbedPane.addTab("📜 Sao Kê & Lịch Sử", historyForm);

        // Tự động tải mới dữ liệu mỗi khi người dùng chuyển tab
        tabbedPane.addChangeListener(e -> {
            int selectedIdx = tabbedPane.getSelectedIndex();
            if (selectedIdx == 0 && transferForm != null) {
                transferForm.refreshBalance();
            } else if (selectedIdx == 1 && billPayForm != null) {
                billPayForm.refreshData();
            } else if (selectedIdx == 2 && savingForm != null) {
                savingForm.loadSavingsData();
            } else if (selectedIdx == 3 && historyForm != null) {
                historyForm.refreshData();
            }
        });

        add(tabbedPane, BorderLayout.CENTER);

        updateBalanceLabel();
    }

    private void toggleBalanceVisibility() {
        isBalanceHidden = !isBalanceHidden;
        if (isBalanceHidden) {
            lblBalance.setText("•••••••• VNĐ");
            btnToggleBalance.setText("🔒");
        } else {
            lblBalance.setText(UITheme.MONEY_FORMAT.format(currentAccount.getBalance()));
            btnToggleBalance.setText("👁");
        }
    }

    /**
     * Cập nhật số dư realtime từ Server và đồng bộ toàn bộ các Form con
     */
    public void updateBalanceLabel() {
        SwingWorker<Double, Void> worker = new SwingWorker<>() {
            @Override
            protected Double doInBackground() throws Exception {
                return bankService.getBalance(currentAccount.getAccountNumber());
            }

            @Override
            protected void done() {
                try {
                    double bal = get();
                    if (bal >= 0) {
                        currentAccount.setBalance(bal);
                        if (!isBalanceHidden) {
                            lblBalance.setText(UITheme.MONEY_FORMAT.format(bal));
                        }
                        if (transferForm != null) {
                            transferForm.refreshBalance();
                        }
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    /**
     * Hộp thoại NẠP TIỀN VÀO TÀI KHOẢN (Mô phỏng slide Bài 8)
     */
    private void openDepositDialog() {
        JDialog dialog = new JDialog(this, "Nạp Tiền Vào Tài Khoản", true);
        dialog.setSize(440, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 25, 20, 25));
        panel.setBackground(Color.WHITE);

        JLabel lblTitle = new JLabel("💳 NẠP TIỀN TRỰC TUYẾN 24/7");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lblTitle);

        panel.add(Box.createVerticalStrut(6));
        JLabel lblDesc = new JLabel("Tài khoản đích: " + currentAccount.getAccountNumber() + " (" + currentAccount.getFullName() + ")");
        lblDesc.setFont(UITheme.FONT_SMALL);
        lblDesc.setForeground(UITheme.TEXT_MUTED);
        panel.add(lblDesc);

        panel.add(Box.createVerticalStrut(16));

        JLabel lblAmt = new JLabel("Số tiền nạp (VNĐ):");
        lblAmt.setFont(UITheme.FONT_BODY_BOLD);
        panel.add(lblAmt);
        panel.add(Box.createVerticalStrut(6));

        ModernTextField txtDepositAmount = new ModernTextField("Nhập số tiền nạp...");
        txtDepositAmount.setText("500000");
        txtDepositAmount.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        panel.add(txtDepositAmount);

        panel.add(Box.createVerticalStrut(10));

        // Mệnh giá nhanh (Chips)
        JPanel chipPanel = new JPanel(new GridLayout(2, 3, 6, 6));
        chipPanel.setOpaque(false);
        chipPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        long[] chips = {100000, 200000, 500000, 1000000, 2000000, 5000000};
        for (long chip : chips) {
            JButton btnChip = new JButton(String.format("%,d đ", chip));
            btnChip.setFont(UITheme.FONT_SMALL_BOLD);
            btnChip.setBackground(UITheme.BG_MAIN);
            btnChip.setForeground(UITheme.PRIMARY_DARK);
            btnChip.setFocusPainted(false);
            btnChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnChip.addActionListener(e -> txtDepositAmount.setText(String.valueOf(chip)));
            chipPanel.add(btnChip);
        }
        panel.add(chipPanel);

        panel.add(Box.createVerticalStrut(14));

        JLabel lblNote = new JLabel("Nội dung nạp:");
        lblNote.setFont(UITheme.FONT_BODY_BOLD);
        panel.add(lblNote);
        panel.add(Box.createVerticalStrut(6));

        ModernTextField txtDepositDesc = new ModernTextField("Nạp tiền từ cổng liên kết");
        txtDepositDesc.setText("Nap tien qua the lien ket");
        txtDepositDesc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        panel.add(txtDepositDesc);

        panel.add(Box.createVerticalStrut(20));

        ModernButton btnConfirm = new ModernButton("XÁC NHẬN NẠP TIỀN", ModernButton.Style.SUCCESS);
        btnConfirm.setFont(UITheme.FONT_BODY_BOLD);
        btnConfirm.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnConfirm.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnConfirm.addActionListener(e -> {
            try {
                double amt = Double.parseDouble(txtDepositAmount.getText().replaceAll("[^0-9]", "").trim());
                if (amt <= 0) {
                    JOptionPane.showMessageDialog(dialog, "Vui lòng nhập số tiền hợp lệ (> 0 VNĐ)!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String note = txtDepositDesc.getText().trim();
                boolean ok = bankService.deposit(currentAccount.getAccountNumber(), amt, note);
                if (ok) {
                    dialog.dispose();
                    updateBalanceLabel();
                    new TransactionReceiptDialog(this, "Biên Lai Nạp Tiền", "NẠP TIỀN THÀNH CÔNG",
                            "Cổng Liên Kết ATM/NAPAS", "Đối tác ngân hàng",
                            currentAccount.getAccountNumber(), currentAccount.getFullName(),
                            amt, note, false).setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(dialog, "Nạp tiền thất bại từ máy chủ!", "Thất Bại", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Lỗi nạp tiền: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(btnConfirm);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    /**
     * Hộp thoại RÚT TIỀN KHỎI TÀI KHOẢN (Mô phỏng slide Bài 8)
     */
    private void openWithdrawDialog() {
        JDialog dialog = new JDialog(this, "Rút Tiền Khỏi Tài Khoản", true);
        dialog.setSize(440, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 25, 20, 25));
        panel.setBackground(Color.WHITE);

        JLabel lblTitle = new JLabel("🏧 RÚT TIỀN MẶT ĐIỆN TỬ");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.DANGER);
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lblTitle);

        panel.add(Box.createVerticalStrut(6));
        JLabel lblDesc = new JLabel("Số dư khả dụng hiện tại: " + UITheme.MONEY_FORMAT.format(currentAccount.getBalance()));
        lblDesc.setFont(UITheme.FONT_SMALL_BOLD);
        lblDesc.setForeground(UITheme.SUCCESS_DARK);
        panel.add(lblDesc);

        panel.add(Box.createVerticalStrut(16));

        JLabel lblAmt = new JLabel("Số tiền muốn rút (VNĐ):");
        lblAmt.setFont(UITheme.FONT_BODY_BOLD);
        panel.add(lblAmt);
        panel.add(Box.createVerticalStrut(6));

        ModernTextField txtWithdrawAmount = new ModernTextField("Nhập số tiền rút...");
        txtWithdrawAmount.setText("200000");
        txtWithdrawAmount.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        panel.add(txtWithdrawAmount);

        panel.add(Box.createVerticalStrut(10));

        // Mệnh giá nhanh (Chips)
        JPanel chipPanel = new JPanel(new GridLayout(2, 3, 6, 6));
        chipPanel.setOpaque(false);
        chipPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        long[] chips = {50000, 100000, 200000, 500000, 1000000, 2000000};
        for (long chip : chips) {
            JButton btnChip = new JButton(String.format("%,d đ", chip));
            btnChip.setFont(UITheme.FONT_SMALL_BOLD);
            btnChip.setBackground(UITheme.BG_MAIN);
            btnChip.setForeground(UITheme.PRIMARY_DARK);
            btnChip.setFocusPainted(false);
            btnChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnChip.addActionListener(e -> txtWithdrawAmount.setText(String.valueOf(chip)));
            chipPanel.add(btnChip);
        }
        panel.add(chipPanel);

        panel.add(Box.createVerticalStrut(14));

        JLabel lblNote = new JLabel("Mục đích rút tiền:");
        lblNote.setFont(UITheme.FONT_BODY_BOLD);
        panel.add(lblNote);
        panel.add(Box.createVerticalStrut(6));

        ModernTextField txtWithdrawDesc = new ModernTextField("Rút tiền tiêu dùng");
        txtWithdrawDesc.setText("Rut tien tieu dung ca nhan");
        txtWithdrawDesc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        panel.add(txtWithdrawDesc);

        panel.add(Box.createVerticalStrut(20));

        ModernButton btnConfirm = new ModernButton("XÁC NHẬN RÚT TIỀN", ModernButton.Style.PRIMARY);
        btnConfirm.setFont(UITheme.FONT_BODY_BOLD);
        btnConfirm.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnConfirm.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnConfirm.addActionListener(e -> {
            try {
                double amt = Double.parseDouble(txtWithdrawAmount.getText().replaceAll("[^0-9]", "").trim());
                if (amt <= 0) {
                    JOptionPane.showMessageDialog(dialog, "Vui lòng nhập số tiền hợp lệ (> 0 VNĐ)!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (amt > currentAccount.getBalance()) {
                    JOptionPane.showMessageDialog(dialog, "Số dư tài khoản không đủ để thực hiện rút tiền!", "Số Dư Không Đủ", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String note = txtWithdrawDesc.getText().trim();
                boolean ok = bankService.withdraw(currentAccount.getAccountNumber(), amt, note);
                if (ok) {
                    dialog.dispose();
                    updateBalanceLabel();
                    new TransactionReceiptDialog(this, "Biên Lai Rút Tiền", "RÚT TIỀN THÀNH CÔNG",
                            currentAccount.getAccountNumber(), currentAccount.getFullName(),
                            "Cây ATM / Quầy Giao Dịch", "Khách hàng",
                            amt, note, true).setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(dialog, "Rút tiền thất bại từ máy chủ!", "Thất Bại", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Lỗi rút tiền: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(btnConfirm);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void performLogout(boolean isWindowClosing) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc chắn muốn đăng xuất khỏi hệ thống e-Banking?",
                "Xác Nhận Đăng Xuất",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                bankService.logout(currentAccount.getUsername());
            } catch (RemoteException e) {
                System.err.println("Không thể dọn dẹp session RMI: " + e.getMessage());
            }

            dispose();

            if (!isWindowClosing) {
                SwingUtilities.invokeLater(() -> {
                    LoginForm loginForm = new LoginForm();
                    loginForm.setVisible(true);
                });
            } else {
                System.exit(0);
            }
        }
    }
}
