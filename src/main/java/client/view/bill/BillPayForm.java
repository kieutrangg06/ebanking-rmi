package client.view.bill;

import client.view.ui.ModernButton;
import client.view.ui.ModernTextField;
import client.view.ui.RoundedPanel;
import client.view.ui.TransactionReceiptDialog;
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
 * Giao diện Thanh Toán Hóa Đơn Trực Tuyến - Tích hợp đồng bộ trong DashboardForm.
 * Thiết kế chuẩn Neo-Banking hiện đại, tra cứu nhanh, kiểm tra trạng thái và thanh toán tức thì với ACID.
 */
public class BillPayForm extends JPanel {

    private final IBankService bankService;
    private final Account account;
    private final Runnable onBalanceUpdated;

    private ModernTextField txtBillCodeSearch;
    private ModernButton btnSearch;

    // Bill detail components
    private ModernTextField txtDetailCode;
    private ModernTextField txtDetailProvider;
    private ModernTextField txtDetailCustomer;
    private JLabel lblDetailAmount;
    private JLabel lblDetailStatus;

    private ModernButton btnPay;
    private ModernButton btnReset;
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
        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_MAIN);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. TOP HEADER & NHÀ CUNG CẤP DỊCH VỤ NHANH
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        // Header Card
        RoundedPanel headerCard = new RoundedPanel(16, Color.WHITE);
        headerCard.setLayout(new BorderLayout(14, 12));
        headerCard.setBorder(new EmptyBorder(16, 20, 16, 20));
        headerCard.setShowShadow(true);

        JPanel titleBox = new JPanel(new BorderLayout(4, 4));
        titleBox.setOpaque(false);

        JLabel lblTitle = new JLabel("🧾 THANH TOÁN HÓA ĐƠN TIỆN ÍCH TRỰC TUYẾN 24/7");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);

        JLabel lblSub = new JLabel("Hỗ trợ gạch nợ tự động tiền Điện (EVN), Nước sinh hoạt, Cáp & Internet (FPT, VNPT, Viettel). Tiền trừ tức thời.");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_MUTED);

        titleBox.add(lblTitle, BorderLayout.NORTH);
        titleBox.add(lblSub, BorderLayout.SOUTH);
        headerCard.add(titleBox, BorderLayout.NORTH);

        // Provider badges row
        JPanel providersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        providersRow.setOpaque(false);

        JLabel lblProviderHint = new JLabel("Dịch vụ liên kết:");
        lblProviderHint.setFont(UITheme.FONT_SMALL_BOLD);
        lblProviderHint.setForeground(UITheme.TEXT_MUTED);
        providersRow.add(lblProviderHint);

        providersRow.add(createProviderChip("⚡ Điện Lực EVN", new Color(254, 243, 199), new Color(146, 64, 14)));
        providersRow.add(createProviderChip("💧 Cấp Nước Sạch", new Color(224, 242, 254), new Color(7, 89, 133)));
        providersRow.add(createProviderChip("🌐 Internet FPT", new Color(255, 237, 213), new Color(154, 52, 18)));
        providersRow.add(createProviderChip("📱 Viễn Thông 4G/5G", new Color(243, 232, 255), new Color(107, 33, 168)));

        headerCard.add(providersRow, BorderLayout.CENTER);
        topPanel.add(headerCard);

        add(topPanel, BorderLayout.NORTH);

        // 2. CENTER CONTENT (Tra cứu + Chi tiết hóa đơn)
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // Khối Tra cứu mã hóa đơn
        RoundedPanel searchCard = new RoundedPanel(16, Color.WHITE);
        searchCard.setLayout(new BorderLayout(10, 10));
        searchCard.setBorder(new EmptyBorder(16, 20, 16, 20));
        searchCard.setShowShadow(true);

        JLabel lblSearchTitle = new JLabel("Tra Cứu Mã Hóa Đơn Khách Hàng:");
        lblSearchTitle.setFont(UITheme.FONT_SUBTITLE);
        lblSearchTitle.setForeground(UITheme.TEXT_MAIN);

        JPanel searchInputRow = new JPanel(new BorderLayout(10, 0));
        searchInputRow.setOpaque(false);

        txtBillCodeSearch = new ModernTextField("Nhập mã hóa đơn (VD: EVN_HANOI_01, WA_DANANG_02, FPT_NET_03)...");
        txtBillCodeSearch.setPreferredSize(new Dimension(300, 38));

        btnSearch = new ModernButton("🔍 Tra Cứu Ngay", ModernButton.Style.PRIMARY);
        btnSearch.setCornerRadius(8);
        btnSearch.addActionListener(e -> performSearchBill());
        txtBillCodeSearch.addActionListener(e -> performSearchBill());

        searchInputRow.add(txtBillCodeSearch, BorderLayout.CENTER);
        searchInputRow.add(btnSearch, BorderLayout.EAST);

        // Quick suggestions buttons
        JPanel quickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        quickPanel.setOpaque(false);
        JLabel lblQuick = new JLabel("Gợi ý mã mẫu:");
        lblQuick.setFont(UITheme.FONT_SMALL_BOLD);
        lblQuick.setForeground(UITheme.TEXT_MUTED);
        quickPanel.add(lblQuick);

        addQuickCodeButton(quickPanel, "EVN_HANOI_01", "⚡ Tiền Điện");
        addQuickCodeButton(quickPanel, "WA_DANANG_02", "💧 Tiền Nước");
        addQuickCodeButton(quickPanel, "FPT_NET_03", "🌐 Internet");

        JPanel searchInner = new JPanel(new BorderLayout(0, 10));
        searchInner.setOpaque(false);
        searchInner.add(searchInputRow, BorderLayout.NORTH);
        searchInner.add(quickPanel, BorderLayout.SOUTH);

        searchCard.add(lblSearchTitle, BorderLayout.NORTH);
        searchCard.add(searchInner, BorderLayout.CENTER);
        centerPanel.add(searchCard);

        centerPanel.add(Box.createVerticalStrut(14));

        // Khối Chi tiết hóa đơn
        RoundedPanel detailCard = new RoundedPanel(16, Color.WHITE);
        detailCard.setLayout(new BoxLayout(detailCard, BoxLayout.Y_AXIS));
        detailCard.setBorder(new EmptyBorder(18, 20, 18, 20));
        detailCard.setShowShadow(true);

        JLabel lblDetailCardTitle = new JLabel("Thông Tin Hóa Đơn Chi Tiết:");
        lblDetailCardTitle.setFont(UITheme.FONT_SUBTITLE);
        lblDetailCardTitle.setForeground(UITheme.TEXT_MAIN);
        detailCard.add(lblDetailCardTitle);
        detailCard.add(Box.createVerticalStrut(12));

        JPanel formGrid = new JPanel(new GridLayout(2, 2, 16, 12));
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

        // Status badge panel
        JPanel statusGroup = new JPanel(new BorderLayout(0, 4));
        statusGroup.setOpaque(false);
        JLabel lblStTitle = new JLabel("Trạng thái nợ cước:");
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

        formGrid.add(createFieldGroup("Mã hóa đơn:", txtDetailCode));
        formGrid.add(createFieldGroup("Loại dịch vụ / Nhà cung cấp:", txtDetailProvider));
        formGrid.add(createFieldGroup("Họ tên khách hàng:", txtDetailCustomer));
        formGrid.add(statusGroup);

        detailCard.add(formGrid);
        detailCard.add(Box.createVerticalStrut(14));

        // Amount banner
        RoundedPanel amountBanner = new RoundedPanel(12, UITheme.PRIMARY_LIGHT);
        amountBanner.setLayout(new BorderLayout(10, 0));
        amountBanner.setBorder(new EmptyBorder(12, 18, 12, 18));
        amountBanner.setShowShadow(false);

        JLabel lblAmtTitle = new JLabel("TỔNG TIỀN PHẢI THANH TOÁN:");
        lblAmtTitle.setFont(UITheme.FONT_BODY_BOLD);
        lblAmtTitle.setForeground(UITheme.PRIMARY_DARK);

        lblDetailAmount = new JLabel("0 VNĐ", SwingConstants.RIGHT);
        lblDetailAmount.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblDetailAmount.setForeground(UITheme.PRIMARY);

        amountBanner.add(lblAmtTitle, BorderLayout.WEST);
        amountBanner.add(lblDetailAmount, BorderLayout.EAST);
        detailCard.add(amountBanner);

        detailCard.add(Box.createVerticalStrut(16));

        // Action buttons
        JPanel actionRow = new JPanel(new BorderLayout());
        actionRow.setOpaque(false);

        lblStatusMsg = new JLabel("Nhập mã hóa đơn hoặc chọn mẫu phía trên để bắt đầu.", SwingConstants.LEFT);
        lblStatusMsg.setFont(UITheme.FONT_BODY);
        lblStatusMsg.setForeground(UITheme.TEXT_MUTED);
        actionRow.add(lblStatusMsg, BorderLayout.WEST);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnBar.setOpaque(false);

        btnReset = new ModernButton("Nhập Lại", ModernButton.Style.SECONDARY);
        btnReset.setCornerRadius(8);
        btnReset.addActionListener(e -> {
            txtBillCodeSearch.setText("");
            clearBillDetails();
            txtBillCodeSearch.requestFocus();
        });

        btnPay = new ModernButton("✔ Xác Nhận Thanh Toán", ModernButton.Style.SUCCESS);
        btnPay.setCornerRadius(8);
        btnPay.setEnabled(false);
        btnPay.addActionListener(e -> performPayBill());

        btnBar.add(btnReset);
        btnBar.add(btnPay);
        actionRow.add(btnBar, BorderLayout.EAST);

        detailCard.add(actionRow);

        centerPanel.add(detailCard);

        JScrollPane scrollPane = new JScrollPane(centerPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JLabel createProviderChip(String text, Color bg, Color fg) {
        JLabel chip = new JLabel(text);
        chip.setFont(UITheme.FONT_SMALL_BOLD);
        chip.setForeground(fg);
        chip.setBackground(bg);
        chip.setOpaque(true);
        chip.setBorder(new EmptyBorder(4, 10, 4, 10));
        return chip;
    }

    private void addQuickCodeButton(JPanel parent, String code, String label) {
        ModernButton btn = new ModernButton(label + " (" + code + ")", ModernButton.Style.OUTLINE);
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

    public void refreshData() {
        if (currentBill != null && !txtDetailCode.getText().trim().isEmpty()) {
            performSearchBill();
        }
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
                        lblStatusMsg.setForeground(UITheme.DANGER);
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
                    lblStatusMsg.setForeground(UITheme.DANGER);
                    JOptionPane.showMessageDialog(BillPayForm.this,
                            "Lỗi khi tra cứu hóa đơn: " + ex.getMessage(),
                            "Lỗi Mạng RMI",
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
            lblDetailStatus.setText("✔ ĐÃ THANH TOÁN (PAID)");
            lblDetailStatus.setForeground(UITheme.SUCCESS_DARK);
            lblDetailStatus.setBackground(UITheme.SUCCESS_LIGHT);
            btnPay.setEnabled(false);
            lblStatusMsg.setText("Hóa đơn này đã được thanh toán hoàn tất. Không cần thanh toán lại.");
            lblStatusMsg.setForeground(UITheme.SUCCESS_DARK);
        } else {
            lblDetailStatus.setText("⚠ CHƯA THANH TOÁN (UNPAID)");
            lblDetailStatus.setForeground(UITheme.DANGER_DARK);
            lblDetailStatus.setBackground(UITheme.DANGER_LIGHT);
            btnPay.setEnabled(true);
            lblStatusMsg.setText("Đã tìm thấy hóa đơn hợp lệ. Nhấn 'Xác Nhận Thanh Toán' để tiếp tục.");
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
        btnPay.setEnabled(false);
        lblStatusMsg.setText("Nhập mã hóa đơn hoặc chọn mẫu phía trên để bắt đầu.");
        lblStatusMsg.setForeground(UITheme.TEXT_MUTED);
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
        lblStatusMsg.setText("Đang thực hiện giao dịch thanh toán an toàn qua Server...");
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
                try {
                    boolean success = get();
                    if (success) {
                        currentBill.setStatus("PAID");
                        displayBill(currentBill);

                        // Cập nhật số dư cục bộ
                        account.setBalance(account.getBalance() - currentBill.getAmount());

                        if (onBalanceUpdated != null) {
                            onBalanceUpdated.run();
                        }

                        // Hiển thị biên lai giao dịch điện tử
                        Window parentWindow = SwingUtilities.getWindowAncestor(BillPayForm.this);
                        TransactionReceiptDialog receiptDialog = new TransactionReceiptDialog(
                                parentWindow,
                                account.getAccountNumber(),
                                account.getFullName(),
                                currentBill.getBillCode(),
                                currentBill.getCustomerName() + " (" + currentBill.getServiceType() + ")",
                                currentBill.getAmount(),
                                "Thanh toán cước: " + currentBill.getServiceType() + " - Mã: " + currentBill.getBillCode()
                        );
                        receiptDialog.setVisible(true);

                    } else {
                        btnPay.setEnabled(true);
                        JOptionPane.showMessageDialog(BillPayForm.this,
                                "Giao dịch thanh toán không thành công. Vui lòng kiểm tra lại số dư hoặc trạng thái hóa đơn!",
                                "Giao Dịch Thất Bại",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    btnPay.setEnabled(true);
                    String msg = errorDetail != null ? errorDetail : ex.getMessage();
                    lblStatusMsg.setText("Thất bại: " + msg);
                    lblStatusMsg.setForeground(UITheme.DANGER);
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
