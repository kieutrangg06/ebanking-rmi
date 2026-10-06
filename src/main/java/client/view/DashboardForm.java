package client.view;

import client.view.auth.LoginForm;
import client.view.bill.BillPayForm;
import client.view.bill.TransactionHistoryForm;
import client.view.transfer.TransferForm;
import client.view.ui.ModernButton;
import client.view.ui.UITheme;
import common.models.Account;
import common.rmi.IBankService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;

public class DashboardForm extends JFrame {
    private final IBankService bankService;
    private final Account currentAccount;
    private JLabel lblBalance;
    private TransferForm transferForm;

    public DashboardForm(IBankService bankService, Account account) {
        this.bankService = bankService;
        this.currentAccount = account;

        setTitle("e-Banking RMI - Không Gian Giao Dịch: " + account.getFullName() + " (STK: " + account.getAccountNumber() + ")");
        setSize(1060, 740);
        setMinimumSize(new Dimension(960, 620));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        // Bắt sự kiện đóng cửa sổ để dọn dẹp session trên Server an toàn
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                performLogout(true);
            }
        });

        // Header thanh điều hướng ngân hàng hiện đại (Top Navbar)
        JPanel topPanel = new JPanel(new BorderLayout(15, 0));
        topPanel.setBackground(Color.WHITE);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 24, 12, 24)
        ));

        // Khối bên trái: Avatar & Thông tin tài khoản
        JPanel userInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        userInfoPanel.setOpaque(false);

        // Avatar tròn viết hoa chữ cái đầu
        JPanel avatarPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UITheme.PRIMARY_LIGHT);
                g2.fillOval(0, 0, 36, 36);
                g2.setColor(UITheme.PRIMARY);
                g2.setFont(UITheme.FONT_TITLE);
                String initial = account.getFullName().isEmpty() ? "U" : account.getFullName().substring(0, 1).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                int x = (36 - fm.stringWidth(initial)) / 2;
                int y = (36 - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initial, x, y);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(36, 36);
            }
        };
        avatarPanel.setOpaque(false);
        userInfoPanel.add(avatarPanel);

        JPanel nameAndAcc = new JPanel(new GridLayout(2, 1, 0, 2));
        nameAndAcc.setOpaque(false);

        JLabel lblUser = new JLabel(account.getFullName().toUpperCase() + "  |  STK: " + account.getAccountNumber());
        lblUser.setFont(UITheme.FONT_BODY_BOLD);
        lblUser.setForeground(UITheme.TEXT_MAIN);

        JLabel lblType = new JLabel("Tài khoản thanh toán trực tuyến (ACTIVE)");
        lblType.setFont(UITheme.FONT_SMALL);
        lblType.setForeground(UITheme.TEXT_MUTED);

        nameAndAcc.add(lblUser);
        nameAndAcc.add(lblType);
        userInfoPanel.add(nameAndAcc);

        // Khối bên phải: Huy hiệu số dư & Nút Đăng Xuất
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightActions.setOpaque(false);

        lblBalance = new JLabel();
        lblBalance.setFont(UITheme.FONT_BODY_BOLD);
        lblBalance.setForeground(UITheme.SUCCESS_DARK);
        lblBalance.setBackground(UITheme.SUCCESS_LIGHT);
        lblBalance.setOpaque(true);
        lblBalance.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        rightActions.add(lblBalance);

        // Nút Người 2: Thanh toán hóa đơn trực tuyến
        ModernButton btnBill = new ModernButton("💳 Thanh Toán Hóa Đơn", ModernButton.Style.OUTLINE);
        btnBill.setFont(UITheme.FONT_SMALL_BOLD);
        btnBill.setCornerRadius(8);
        btnBill.setMargin(new Insets(6, 12, 6, 12));
        btnBill.addActionListener(e -> {
            BillPayForm form = new BillPayForm(bankService, currentAccount, this::updateBalanceLabel);
            form.setVisible(true);
        });
        rightActions.add(btnBill);

        // Nút Người 2: Lịch sử giao dịch & xuất sao kê CSV
        ModernButton btnHistory = new ModernButton("📜 Lịch Sử Giao Dịch", ModernButton.Style.OUTLINE);
        btnHistory.setFont(UITheme.FONT_SMALL_BOLD);
        btnHistory.setCornerRadius(8);
        btnHistory.setMargin(new Insets(6, 12, 6, 12));
        btnHistory.addActionListener(e -> {
            TransactionHistoryForm form = new TransactionHistoryForm(bankService, currentAccount);
            form.setVisible(true);
        });
        rightActions.add(btnHistory);

        ModernButton btnLogout = new ModernButton("Đăng Xuất", ModernButton.Style.DANGER);
        btnLogout.setFont(UITheme.FONT_SMALL_BOLD);
        btnLogout.setCornerRadius(8);
        btnLogout.setMargin(new Insets(6, 14, 6, 14));
        btnLogout.addActionListener(e -> performLogout(false));
        rightActions.add(btnLogout);

        topPanel.add(userInfoPanel, BorderLayout.WEST);
        topPanel.add(rightActions, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        updateBalanceLabel();

        // Không gian làm việc cốt lõi của Người 1: Chuyển khoản trực tuyến & Quản lý số dư
        transferForm = new TransferForm(bankService, currentAccount, () -> updateBalanceLabel());
        add(transferForm, BorderLayout.CENTER);
    }

    public void updateBalanceLabel() {
        try {
            double bal = bankService.getBalance(currentAccount.getAccountNumber());
            currentAccount.setBalance(bal);
            lblBalance.setText("Số Dư: " + UITheme.MONEY_FORMAT.format(bal));
            if (transferForm != null) {
                transferForm.refreshBalance();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void performLogout(boolean exitOnConfirm) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn đăng xuất khỏi phiên làm việc?",
                "Xác Nhận Đăng Xuất",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                bankService.logout(currentAccount.getUsername());
            } catch (RemoteException ex) {
                System.err.println("Lỗi gọi logout tới server: " + ex.getMessage());
            }
            dispose();
            if (exitOnConfirm) {
                System.exit(0);
            } else {
                new LoginForm().setVisible(true);
            }
        }
    }
}
