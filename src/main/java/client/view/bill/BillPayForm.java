package client.view.bill;

import client.view.ui.ModernButton;
import client.view.ui.ModernTextField;
import client.view.ui.RoundedPanel;
import client.view.ui.UITheme;
import common.models.Account;
import common.models.Bill;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.rmi.RemoteException;
import java.util.concurrent.ExecutionException;

/**
 * Giao diện thanh toán hóa đơn trực tuyến (Người 2 - Pure Java Swing).
 * Tích hợp chuẩn UITheme, ModernButton, ModernTextField, RoundedPanel của Dev 1.
 * Sử dụng SwingWorker cho tất cả cuộc gọi RMI để giao diện không bị giật lag/đơ.
 */
public class BillPayForm extends JFrame {

    private final IBankService bankService;
    private final Account account;
    private final Runnable onBalanceUpdated;

    private ModernTextField txtBillCodeSearch;
    private ModernButton btnSearch;
    private JLabel lblAccountBalance;

    // Bill detail components
    private ModernTextField txtDetailCode;
    private ModernTextField txtDetailProvider;
    private ModernTextField txtDetailCustomer;
    private JLabel lblDetailAmount;
    private JLabel lblDetailStatus;

    private ModernButton btnPay;
    private ModernButton btnClose;
    private JLabel lblStatusMsg;

    private Bill currentBill;

    public BillPayForm(IBankService bankService, Account account) {
        this(bankService, account, null);
    }

    public BillPayForm(IBankService bankService, Account account, Runnable onBalanceUpdated) {
        this.bankService = bankService;
        this.account = account;
        this.onBalanceUpdated = onBalanceUpdated;

        initUI();
    }

    private void initUI() {
        setTitle("Thanh Toán Hóa Đơn Trực Tuyến - " + account.getFullName() + " (STK: " + account.getAccountNumber() + ")");
        setSize(700, 620);
        setMinimumSize(new Dimension(640, 560));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(UITheme.BG_MAIN);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(UITheme.BG_MAIN);
        mainPanel.setBorder(new EmptyBorder(20, 24, 20, 24));

        // 1. TOP HEADER & ACCOUNT BADGE
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setOpaque(false);

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 4));
        titlePanel.setOpaque(false);
        JLabel lblTitle = new JLabel("THANH TOÁN HÓA ĐƠN TRỰC TUYẾN");
        lblTitle.setFont(UITheme.FONT_TITLE_XL);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);

        JLabel lblSub = new JLabel("Thanh toán tiền Điện (EVN), Nước sạch, Internet (FPT)... an toàn qua RMI");
        lblSub.setFont(UITheme.FONT_BODY);
        lblSub.setForeground(UITheme.TEXT_MUTED);
        titlePanel.add(lblTitle);
        titlePanel.add(lblSub);

        // Account balance badge
        RoundedPanel badgeAcc = new RoundedPanel(12, Color.WHITE);
        badgeAcc.setLayout(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        badgeAcc.setBorder(new EmptyBorder(2, 10, 2, 10));
        badgeAcc.setShowShadow(false);

        JLabel lblAccInfo = new JLabel("STK: " + account.getAccountNumber() + " | " + account.getFullName());
        lblAccInfo.setFont(UITheme.FONT_BODY_BOLD);
        lblAccInfo.setForeground(UITheme.TEXT_MAIN);

        lblAccountBalance = new JLabel(UITheme.MONEY_FORMAT.format(account.getBalance()));
        lblAccountBalance.setFont(UITheme.FONT_BODY_BOLD);
        lblAccountBalance.setForeground(UITheme.SUCCESS_DARK);
        lblAccountBalance.setBackground(UITheme.SUCCESS_LIGHT);
        lblAccountBalance.setOpaque(true);
        lblAccountBalance.setBorder(new EmptyBorder(4, 10, 4, 10));

        badgeAcc.add(lblAccInfo);
        badgeAcc.add(lblAccountBalance);

        topPanel.add(titlePanel, BorderLayout.WEST);
        topPanel.add(badgeAcc, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 2. CENTER CONTENT (Search box + Bill details card)
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // Khối Tra cứu mã hóa đơn
        RoundedPanel searchCard = new RoundedPanel(14, Color.WHITE);
        searchCard.setLayout(new BorderLayout(10, 10));
        searchCard.setBorder(new EmptyBorder(14, 16, 14, 16));
        searchCard.setShowShadow(false);

        JLabel lblSearchTitle = new JLabel("Tra cứu mã hóa đơn cần thanh toán:");
        lblSearchTitle.setFont(UITheme.FONT_SUBTITLE);
        lblSearchTitle.setForeground(UITheme.TEXT_MAIN);

        JPanel searchInputRow = new JPanel(new BorderLayout(10, 0));
        searchInputRow.setOpaque(false);

        txtBillCodeSearch = new ModernTextField("Nhập mã hóa đơn (VD: EVN_HANOI_01, WA_DANANG_02, FPT_NET_03)...");
        btnSearch = new ModernButton("  Tra Cứu Hóa Đơn  ", ModernButton.Style.PRIMARY);
        btnSearch.addActionListener(e -> performSearchBill());
        txtBillCodeSearch.addActionListener(e -> performSearchBill());

        searchInputRow.add(txtBillCodeSearch, BorderLayout.CENTER);
        searchInputRow.add(btnSearch, BorderLayout.EAST);

        // Quick suggestions buttons
        JPanel quickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        quickPanel.setOpaque(false);
        JLabel lblQuick = new JLabel("Hóa đơn mẫu:");
        lblQuick.setFont(UITheme.FONT_SMALL_BOLD);
        lblQuick.setForeground(UITheme.TEXT_MUTED);
        quickPanel.add(lblQuick);

        addQuickCodeButton(quickPanel, "EVN_HANOI_01");
        addQuickCodeButton(quickPanel, "WA_DANANG_02");
        addQuickCodeButton(quickPanel, "FPT_NET_03");

        JPanel searchInner = new JPanel(new GridLayout(2, 1, 0, 8));
        searchInner.setOpaque(false);
        searchInner.add(searchInputRow);
        searchInner.add(quickPanel);

        searchCard.add(lblSearchTitle, BorderLayout.NORTH);
        searchCard.add(searchInner, BorderLayout.CENTER);
        centerPanel.add(searchCard);

        centerPanel.add(Box.createVerticalStrut(14));

        // Khối Chi tiết hóa đơn
        RoundedPanel detailCard = new RoundedPanel(14, Color.WHITE);
        detailCard.setLayout(new BoxLayout(detailCard, BoxLayout.Y_AXIS));
        detailCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        detailCard.setShowShadow(false);

        JLabel lblDetailCardTitle = new JLabel("Thông tin hóa đơn chi tiết:");
        lblDetailCardTitle.setFont(UITheme.FONT_SUBTITLE);
        lblDetailCardTitle.setForeground(UITheme.TEXT_MAIN);
        detailCard.add(lblDetailCardTitle);
        detailCard.add(Box.createVerticalStrut(12));

        JPanel formGrid = new JPanel(new GridLayout(4, 2, 14, 10));
        formGrid.setOpaque(false);

        txtDetailCode = new ModernTextField();
        txtDetailCode.setEditable(false);
        txtDetailCode.setBackground(UITheme.BG_INPUT);

        txtDetailProvider = new ModernTextField();
        txtDetailProvider.setEditable(false);
        txtDetailProvider.setBackground(UITheme.BG_INPUT);

        txtDetailCustomer = new ModernTextField();
        txtDetailCustomer.setEditable(false);
        txtDetailCustomer.setBackground(UITheme.BG_INPUT);

        formGrid.add(createFieldGroup("Mã hóa đơn:", txtDetailCode));
        formGrid.add(createFieldGroup("Loại dịch vụ / Nhà cung cấp:", txtDetailProvider));
        formGrid.add(createFieldGroup("Khách hàng đăng ký:", txtDetailCustomer));

        // Status badge panel
        JPanel statusGroup = new JPanel(new BorderLayout(0, 4));
        statusGroup.setOpaque(false);
        JLabel lblStTitle = new JLabel("Trạng thái gạch nợ:");
        lblStTitle.setFont(UITheme.FONT_SMALL_BOLD);
        lblStTitle.setForeground(UITheme.TEXT_MUTED);

        lblDetailStatus = new JLabel("Chưa tra cứu", SwingConstants.CENTER);
        lblDetailStatus.setFont(UITheme.FONT_BODY_BOLD);
        lblDetailStatus.setForeground(UITheme.TEXT_MUTED);
        lblDetailStatus.setBackground(new Color(241, 245, 249));
        lblDetailStatus.setOpaque(true);
        lblDetailStatus.setBorder(new EmptyBorder(8, 14, 8, 14));
        statusGroup.add(lblStTitle, BorderLayout.NORTH);
        statusGroup.add(lblDetailStatus, BorderLayout.CENTER);
        formGrid.add(statusGroup);

        detailCard.add(formGrid);
        detailCard.add(Box.createVerticalStrut(14));

        // Amount banner
        JPanel amountBanner = new JPanel(new BorderLayout());
        amountBanner.setBackground(UITheme.PRIMARY_LIGHT);
        amountBanner.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lblAmtTitle = new JLabel("Tổng tiền thanh toán:");
        lblAmtTitle.setFont(UITheme.FONT_BODY_BOLD);
        lblAmtTitle.setForeground(UITheme.PRIMARY_HOVER);

        lblDetailAmount = new JLabel("0 VNĐ", SwingConstants.RIGHT);
        lblDetailAmount.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblDetailAmount.setForeground(UITheme.PRIMARY);

        amountBanner.add(lblAmtTitle, BorderLayout.WEST);
        amountBanner.add(lblDetailAmount, BorderLayout.EAST);
        detailCard.add(amountBanner);

        centerPanel.add(detailCard);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // 3. BOTTOM ACTIONS & STATUS MESSAGE
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 8));
        bottomPanel.setOpaque(false);

        lblStatusMsg = new JLabel(" ", SwingConstants.LEFT);
        lblStatusMsg.setFont(UITheme.FONT_BODY);
        lblStatusMsg.setForeground(UITheme.TEXT_MUTED);
        bottomPanel.add(lblStatusMsg, BorderLayout.NORTH);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnBar.setOpaque(false);

        btnClose = new ModernButton("  Đóng  ", ModernButton.Style.SECONDARY);
        btnClose.addActionListener(e -> dispose());

        btnPay = new ModernButton("  Xác Nhận Thanh Toán Hóa Đơn  ", ModernButton.Style.SUCCESS);
        btnPay.setEnabled(false);
        btnPay.addActionListener(e -> performPayBill());

        btnBar.add(btnClose);
        btnBar.add(btnPay);
        bottomPanel.add(btnBar, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void addQuickCodeButton(JPanel parent, String code) {
        ModernButton btn = new ModernButton(code, ModernButton.Style.OUTLINE);
        btn.setFont(UITheme.FONT_SMALL_BOLD);
        btn.setCornerRadius(6);
        btn.setMargin(new Insets(3, 8, 3, 8));
        btn.addActionListener(e -> {
            txtBillCodeSearch.setText(code);
            performSearchBill();
        });
        parent.add(btn);
    }

    private JPanel createFieldGroup(String label, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(UITheme.FONT_SMALL_BOLD);
        l.setForeground(UITheme.TEXT_MUTED);
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private void performSearchBill() {
        String code = txtBillCodeSearch.getText().trim();
        if (code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập mã hóa đơn cần tra cứu!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            txtBillCodeSearch.requestFocus();
            return;
        }

        btnSearch.setEnabled(false);
        btnPay.setEnabled(false);
        lblStatusMsg.setText("Đang tra cứu dữ liệu hóa đơn từ Server...");
        lblStatusMsg.setForeground(UITheme.PRIMARY);

        SwingWorker<Bill, Void> worker = new SwingWorker<>() {
            @Override
            protected Bill doInBackground() throws Exception {
                return bankService.queryBill(code);
            }

            @Override
            protected void done() {
                btnSearch.setEnabled(true);
                try {
                    Bill b = get();
                    if (b == null) {
                        currentBill = null;
                        clearBillDetails();
                        lblStatusMsg.setText("Không tìm thấy thông tin hóa đơn có mã: " + code);
                        lblStatusMsg.setForeground(UITheme.DANGER_DARK);
                        JOptionPane.showMessageDialog(BillPayForm.this,
                                "Không tìm thấy hóa đơn: " + code + "\nVui lòng kiểm tra lại mã hoặc liên hệ nhà cung cấp.",
                                "Không Tìm Thấy Hóa Đơn",
                                JOptionPane.WARNING_MESSAGE);
                    } else {
                        currentBill = b;
                        displayBill(b);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    lblStatusMsg.setText("Lỗi kết nối tới Server: " + ex.getMessage());
                    lblStatusMsg.setForeground(UITheme.DANGER_DARK);
                    JOptionPane.showMessageDialog(BillPayForm.this,
                            "Lỗi khi tra cứu hóa đơn: " + ex.getMessage(),
                            "Lỗi Mạng",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void displayBill(Bill bill) {
        txtDetailCode.setText(bill.getBillCode());
        txtDetailProvider.setText(bill.getServiceType());
        txtDetailCustomer.setText(bill.getCustomerName());
        lblDetailAmount.setText(UITheme.MONEY_FORMAT.format(bill.getAmount()));

        if ("PAID".equalsIgnoreCase(bill.getStatus())) {
            lblDetailStatus.setText("ĐÃ THANH TOÁN (PAID)");
            lblDetailStatus.setForeground(UITheme.SUCCESS_DARK);
            lblDetailStatus.setBackground(UITheme.SUCCESS_LIGHT);
            btnPay.setEnabled(false);
            lblStatusMsg.setText("Hóa đơn này đã được thanh toán trước đó. Không cần thanh toán lại.");
            lblStatusMsg.setForeground(UITheme.SUCCESS_DARK);
        } else {
            lblDetailStatus.setText("CHƯA THANH TOÁN (UNPAID)");
            lblDetailStatus.setForeground(UITheme.DANGER_DARK);
            lblDetailStatus.setBackground(UITheme.DANGER_LIGHT);
            btnPay.setEnabled(true);
            lblStatusMsg.setText("Đã tìm thấy hóa đơn hợp lệ. Sẵn sàng thanh toán.");
            lblStatusMsg.setForeground(UITheme.TEXT_MAIN);
        }
    }

    private void clearBillDetails() {
        txtDetailCode.setText("");
        txtDetailProvider.setText("");
        txtDetailCustomer.setText("");
        lblDetailAmount.setText("0 VNĐ");
        lblDetailStatus.setText("Chưa tra cứu");
        lblDetailStatus.setForeground(UITheme.TEXT_MUTED);
        lblDetailStatus.setBackground(new Color(241, 245, 249));
    }

    private void performPayBill() {
        if (currentBill == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng tra cứu hóa đơn trước khi thanh toán!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if ("PAID".equalsIgnoreCase(currentBill.getStatus())) {
            JOptionPane.showMessageDialog(this, "Hóa đơn này đã được thanh toán rồi!", "Cảnh Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (account.getBalance() < currentBill.getAmount()) {
            JOptionPane.showMessageDialog(this,
                    "Số dư khả dụng không đủ để thanh toán hóa đơn này!\n"
                            + "Số dư hiện tại: " + UITheme.MONEY_FORMAT.format(account.getBalance()) + "\n"
                            + "Số tiền cần thanh toán: " + UITheme.MONEY_FORMAT.format(currentBill.getAmount()),
                    "Số Dư Không Đủ",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Xác nhận thanh toán
        String confirmMsg = "XÁC NHẬN GIAO DỊCH THANH TOÁN HÓA ĐƠN:\n\n"
                + "• Mã hóa đơn: " + currentBill.getBillCode() + "\n"
                + "• Dịch vụ: " + currentBill.getServiceType() + "\n"
                + "• Khách hàng: " + currentBill.getCustomerName() + "\n"
                + "• Số tiền: " + UITheme.MONEY_FORMAT.format(currentBill.getAmount()) + "\n"
                + "• Trừ từ STK: " + account.getAccountNumber() + "\n\n"
                + "Bạn có chắc chắn muốn thực hiện giao dịch này không?";

        int choice = JOptionPane.showConfirmDialog(this, confirmMsg, "Xác Nhận Thanh Toán", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        btnPay.setEnabled(false);
        btnClose.setEnabled(false);
        lblStatusMsg.setText("Đang thực hiện giao dịch thanh toán ACID qua Server...");
        lblStatusMsg.setForeground(UITheme.PRIMARY);

        SwingWorker<Boolean, Void> payWorker = new SwingWorker<>() {
            private String errorDetail = null;

            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    return bankService.payBill(account.getAccountNumber(), currentBill.getBillCode());
                } catch (RemoteException re) {
                    errorDetail = re.getMessage();
                    throw re;
                }
            }

            @Override
            protected void done() {
                btnClose.setEnabled(true);
                try {
                    boolean success = get();
                    if (success) {
                        currentBill.setStatus("PAID");
                        displayBill(currentBill);

                        // Cập nhật số dư cục bộ
                        account.setBalance(account.getBalance() - currentBill.getAmount());
                        lblAccountBalance.setText(UITheme.MONEY_FORMAT.format(account.getBalance()));

                        if (onBalanceUpdated != null) {
                            onBalanceUpdated.run();
                        }

                        JOptionPane.showMessageDialog(BillPayForm.this,
                                "THANH TOÁN HÓA ĐƠN THÀNH CÔNG!\n\n"
                                        + "Mã hóa đơn: " + currentBill.getBillCode() + "\n"
                                        + "Số tiền: " + UITheme.MONEY_FORMAT.format(currentBill.getAmount()) + "\n"
                                        + "Số dư còn lại: " + UITheme.MONEY_FORMAT.format(account.getBalance()),
                                "Thành Công",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        btnPay.setEnabled(true);
                        JOptionPane.showMessageDialog(BillPayForm.this,
                                "Giao dịch thanh toán không thành công. Vui lòng thử lại!",
                                "Giao Dịch Thất Bại",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    btnPay.setEnabled(true);
                    String msg = errorDetail != null ? errorDetail : ex.getMessage();
                    lblStatusMsg.setText("Thất bại: " + msg);
                    lblStatusMsg.setForeground(UITheme.DANGER_DARK);
                    JOptionPane.showMessageDialog(BillPayForm.this,
                            "Lỗi thanh toán: " + msg,
                            "Lỗi Giao Dịch",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        payWorker.execute();
    }
}
