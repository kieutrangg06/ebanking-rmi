package client.view;

import client.view.auth.LoginForm;
import client.view.saving.SavingForm;
import client.view.transfer.TransferForm;
import client.view.ui.ModernButton;
import client.view.ui.ModernTextField;
import client.view.ui.RoundedPanel;
import client.view.ui.UITheme;
import common.models.Account;
import common.models.Bill;
import common.models.Transaction;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Giao diện Không Gian Giao Dịch Ngân Hàng Số Dành Riêng Cho Khách Hàng (Customer Portal).
 * Thiết kế giao diện hiện đại theo chuẩn Fintech, xóa sạch mọi vết phân chia người làm (Dev 1, 2, 3),
 * bảo mật tuyệt đối không lộ tính năng quản trị Admin cho khách hàng thông thường.
 */
public class DashboardForm extends JFrame {

    private final IBankService bankService;
    private final Account currentAccount;

    private JLabel lblBalance;
    private TransferForm transferForm;
    private SavingForm savingForm;
    private DefaultTableModel historyTableModel;

    public DashboardForm(IBankService bankService, Account account) {
        this.bankService = bankService;
        this.currentAccount = account;

        setTitle("e-Banking RMI - Không Gian Giao Dịch: " + account.getFullName() + " (STK: " + account.getAccountNumber() + ")");
        setSize(980, 720);
        setMinimumSize(new Dimension(880, 620));
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

        // --- 1. HEADER THANH ĐIỀU HƯỚNG NGÂN HÀNG HIỆN ĐẠI (TOP NAVBAR) ---
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
                String initial = currentAccount.getFullName().isEmpty() ? "U" : currentAccount.getFullName().substring(0, 1).toUpperCase();
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

        JLabel lblUser = new JLabel(currentAccount.getFullName().toUpperCase() + "  |  STK: " + currentAccount.getAccountNumber());
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

        ModernButton btnLogout = new ModernButton("Đăng Xuất", ModernButton.Style.DANGER);
        btnLogout.setFont(UITheme.FONT_SMALL_BOLD);
        btnLogout.setCornerRadius(8);
        btnLogout.setMargin(new Insets(6, 14, 6, 14));
        btnLogout.addActionListener(e -> performLogout(false));
        rightActions.add(btnLogout);

        topPanel.add(userInfoPanel, BorderLayout.WEST);
        topPanel.add(rightActions, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // --- 2. CÁC TAB NGHIỆP VỤ NGÂN HÀNG (CUSTOMER SERVICES) ---
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 13));

        // Tab 1: Chuyển Tiền 24/7 (Phần Người 1)
        transferForm = new TransferForm(bankService, currentAccount, this::updateBalanceLabel);
        tabbedPane.addTab("💸 Chuyển Tiền Nhanh 24/7", transferForm);

        // Tab 2: Thanh Toán Hóa Đơn (Phần Người 2)
        tabbedPane.addTab("🧾 Thanh Toán Hóa Đơn", createBillPanel());

        // Tab 3: Tiết Kiệm Tích Lũy Online (Phần Người 3 - Bạn)
        savingForm = new SavingForm(bankService, currentAccount, this::updateBalanceLabel);
        tabbedPane.addTab("💰 Tiết Kiệm Tích Lũy Online", savingForm);

        // Tab 4: Sao Kê Lịch Sử Giao Dịch (Phần Người 2)
        tabbedPane.addTab("📜 Sao Kê & Biến Động Số Dư", createHistoryPanel());

        add(tabbedPane, BorderLayout.CENTER);

        updateBalanceLabel();
    }

    public void updateBalanceLabel() {
        try {
            double bal = bankService.getBalance(currentAccount.getAccountNumber());
            currentAccount.setBalance(bal);
            lblBalance.setText("Số Dư: " + UITheme.MONEY_FORMAT.format(bal));
            if (transferForm != null) {
                transferForm.refreshBalance();
            }
            if (savingForm != null) {
                savingForm.loadSavingsData();
            }
            if (historyTableModel != null) {
                loadHistoryData();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TAB 2: THANH TOÁN HÓA ĐƠN TIỆN ÍCH (ĐIỆN / NƯỚC / INTERNET)
    // =========================================================================
    private JPanel createBillPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(UITheme.BG_MAIN);
        panel.setBorder(new EmptyBorder(20, 25, 20, 25));

        RoundedPanel card = new RoundedPanel(16, Color.WHITE);
        card.setLayout(new BorderLayout(15, 15));
        card.setBorder(new EmptyBorder(20, 25, 20, 25));
        card.setShowShadow(true);

        JLabel lblTitle = new JLabel("TRA CỨU & THANH TOÁN HÓA ĐƠN TIỆN ÍCH TRỰC TUYẾN");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);
        card.add(lblTitle, BorderLayout.NORTH);

        JPanel centerBox = new JPanel(new GridLayout(4, 2, 12, 12));
        centerBox.setOpaque(false);

        ModernTextField txtBillCode = new ModernTextField("Nhập mã hóa đơn (VD: EVN_HANOI_01, WA_DANANG_02, FPT_NET_03)");
        JLabel lblServiceType = new JLabel("---");
        JLabel lblCustomerName = new JLabel("---");
        JLabel lblAmount = new JLabel("---");
        JLabel lblStatus = new JLabel("---");

        centerBox.add(new JLabel("Mã Hóa Đơn Cần Tra Cứu:"));
        centerBox.add(txtBillCode);
        centerBox.add(new JLabel("Dịch Vụ:"));
        centerBox.add(lblServiceType);
        centerBox.add(new JLabel("Tên Khách Hàng:"));
        centerBox.add(lblCustomerName);
        centerBox.add(new JLabel("Số Tiền Phải Trả:"));
        centerBox.add(lblAmount);

        card.add(centerBox, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnPanel.setOpaque(false);

        ModernButton btnCheck = new ModernButton("🔍 Tra Cứu Hóa Đơn", ModernButton.Style.SECONDARY);
        ModernButton btnPay = new ModernButton("💳 Xác Nhận Thanh Toán", ModernButton.Style.PRIMARY);
        btnPanel.add(btnCheck);
        btnPanel.add(btnPay);

        card.add(btnPanel, BorderLayout.SOUTH);
        panel.add(card, BorderLayout.NORTH);

        btnCheck.addActionListener(e -> {
            String code = txtBillCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập mã hóa đơn!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                Bill bill = bankService.queryBill(code);
                if (bill != null) {
                    lblServiceType.setText(bill.getServiceType());
                    lblCustomerName.setText(bill.getCustomerName());
                    lblAmount.setText(UITheme.MONEY_FORMAT.format(bill.getAmount()));
                    lblStatus.setText(bill.getStatus());
                } else {
                    JOptionPane.showMessageDialog(this, "Không tìm thấy thông tin hóa đơn với mã: " + code, "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        });

        btnPay.addActionListener(e -> {
            String code = txtBillCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng tra cứu mã hóa đơn trước khi thanh toán!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                boolean ok = bankService.payBill(currentAccount.getAccountNumber(), code);
                if (ok) {
                    JOptionPane.showMessageDialog(this, "Thanh toán hóa đơn " + code + " thành công!", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    updateBalanceLabel();
                    lblStatus.setText("PAID");
                } else {
                    JOptionPane.showMessageDialog(this, "Thanh toán thất bại: Số dư không đủ hoặc hóa đơn đã được thanh toán!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        });

        return panel;
    }

    // =========================================================================
    // TAB 4: SAO KÊ & LỊCH SỬ BIẾN ĐỘNG SỐ DƯ
    // =========================================================================
    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(UITheme.BG_MAIN);
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));

        RoundedPanel card = new RoundedPanel(16, Color.WHITE);
        card.setLayout(new BorderLayout(10, 10));
        card.setBorder(new EmptyBorder(15, 20, 15, 20));
        card.setShowShadow(true);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JLabel lblTitle = new JLabel("LỊCH SỬ BIẾN ĐỘNG SỐ DƯ & SAO KÊ TÀI KHOẢN");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);

        ModernButton btnReload = new ModernButton("🔄 Tải Lại Dữ Liệu", ModernButton.Style.SECONDARY);
        btnReload.setFont(UITheme.FONT_SMALL_BOLD);
        btnReload.addActionListener(e -> loadHistoryData());

        topBar.add(lblTitle, BorderLayout.WEST);
        topBar.add(btnReload, BorderLayout.EAST);
        card.add(topBar, BorderLayout.NORTH);

        String[] cols = {"Thời Gian", "Loại Giao Dịch", "Tài Khoản Gửi", "Tài Khoản Nhận", "Số Tiền", "Nội Dung Giao Dịch"};
        historyTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(historyTableModel);
        table.setRowHeight(28);
        table.setFont(UITheme.FONT_BODY);
        table.getTableHeader().setFont(UITheme.FONT_BODY_BOLD);
        table.getTableHeader().setBackground(UITheme.PRIMARY_LIGHT);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(center);
        table.getColumnModel().getColumn(1).setCellRenderer(center);
        table.getColumnModel().getColumn(2).setCellRenderer(center);
        table.getColumnModel().getColumn(3).setCellRenderer(center);

        card.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(card, BorderLayout.CENTER);

        loadHistoryData();
        return panel;
    }

    private void loadHistoryData() {
        if (historyTableModel == null) return;
        SwingUtilities.invokeLater(() -> {
            try {
                historyTableModel.setRowCount(0);
                List<Transaction> list = bankService.getTransactionHistory(currentAccount.getAccountNumber());
                for (Transaction t : list) {
                    historyTableModel.addRow(new Object[]{
                            t.getCreatedAt(),
                            t.getTransactionType(),
                            t.getFromAccount(),
                            t.getToAccount(),
                            UITheme.MONEY_FORMAT.format(t.getAmount()),
                            t.getDescription()
                    });
                }
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        });
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
