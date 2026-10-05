package client.view.transfer;

import client.view.ui.*;
import common.models.Account;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.rmi.RemoteException;

public class TransferForm extends JPanel {
    private final IBankService bankService;
    private final Account currentAccount;
    private final Runnable onTransferSuccessCallback;

    // Card Components
    private JLabel lblBalanceValue;
    private JButton btnToggleHideBal;
    private ModernButton btnRefreshBal;
    private boolean isBalanceHidden = false;
    private double currentBalanceCache = 0.0;

    // Form Components
    private ModernTextField txtReceiverAcc;
    private ModernButton btnLookupReceiver;
    private RoundedPanel pnlReceiverInfo;
    private JLabel lblReceiverName;
    private String verifiedReceiverName = null;

    private ModernTextField txtAmount;
    private JLabel lblAmountWords;

    private ModernTextField txtDescription;
    private JLabel lblMessage;
    private ModernButton btnTransfer;
    private ModernButton btnReset;

    public TransferForm(IBankService bankService, Account currentAccount, Runnable onTransferSuccessCallback) {
        this.bankService = bankService;
        this.currentAccount = currentAccount;
        this.onTransferSuccessCallback = onTransferSuccessCallback;
        this.currentBalanceCache = currentAccount.getBalance();

        initComponents();
        refreshBalance();
    }

    private void initComponents() {
        setLayout(new BorderLayout(0, 15));
        setBackground(UITheme.BG_MAIN);
        setBorder(new EmptyBorder(15, 20, 15, 20));

        // 1. THẺ NGÂN HÀNG SỐ DƯ HIỆN ĐẠI (DIGITAL DEBIT CARD)
        JPanel topPanel = createCardPanel();
        add(topPanel, BorderLayout.NORTH);

        // 2. FORM NHẬP LIỆU CHUYỂN KHOẢN (INSIDE A SCROLL PANE FOR RESPONSIVENESS)
        JPanel mainFormPanel = createTransferFormPanel();
        JScrollPane scrollPane = new JScrollPane(mainFormPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Tạo Card thẻ ghi nợ với thiết kế kỹ thuật số sang trọng (Navy & Sapphire Gradient)
     */
    private JPanel createCardPanel() {
        RoundedPanel card = new RoundedPanel(18, UITheme.CARD_GRADIENT_START, UITheme.CARD_GRADIENT_END);
        card.setLayout(new BorderLayout(15, 10));
        card.setBorder(new EmptyBorder(16, 22, 16, 22));
        card.setShowShadow(true);
        card.setPreferredSize(new Dimension(0, 130));

        // Cột trái: Chip, Logo, Tên chủ thẻ & STK
        JPanel leftBox = new JPanel();
        leftBox.setLayout(new BoxLayout(leftBox, BoxLayout.Y_AXIS));
        leftBox.setOpaque(false);

        // Header thẻ: Brand & Chip
        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandRow.setOpaque(false);
        brandRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Vẽ chip thẻ ngân hàng
        JPanel chipPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(245, 190, 75));
                g2.fillRoundRect(0, 0, 28, 20, 6, 6);
                g2.setColor(new Color(210, 155, 30));
                g2.drawRoundRect(0, 0, 27, 19, 6, 6);
                g2.drawLine(9, 0, 9, 20);
                g2.drawLine(18, 0, 18, 20);
                g2.drawLine(0, 10, 28, 10);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(28, 20);
            }
        };
        chipPanel.setOpaque(false);
        brandRow.add(chipPanel);

        JLabel lblBrand = new JLabel("e-Banking DIGITAL DEBIT PLATINUM");
        lblBrand.setFont(UITheme.FONT_SMALL_BOLD);
        lblBrand.setForeground(new Color(200, 220, 255));
        brandRow.add(lblBrand);
        leftBox.add(brandRow);

        leftBox.add(Box.createVerticalStrut(10));

        // Chủ tài khoản
        JLabel lblOwner = new JLabel(currentAccount.getFullName().toUpperCase());
        lblOwner.setFont(UITheme.FONT_TITLE);
        lblOwner.setForeground(Color.WHITE);
        lblOwner.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftBox.add(lblOwner);

        // Số tài khoản định dạng đẹp
        JLabel lblAccNum = new JLabel("STK: " + currentAccount.getAccountNumber() + "  |  Tài Khoản Thanh Toán");
        lblAccNum.setFont(UITheme.FONT_MONO);
        lblAccNum.setForeground(new Color(180, 205, 245));
        lblAccNum.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftBox.add(lblAccNum);

        card.add(leftBox, BorderLayout.WEST);

        // Cột phải: Số dư khả dụng & nút ẩn/hiện & nút làm mới
        JPanel rightBox = new JPanel();
        rightBox.setLayout(new BoxLayout(rightBox, BoxLayout.Y_AXIS));
        rightBox.setOpaque(false);

        JLabel lblBalTitle = new JLabel("SỐ DƯ KHẢ DỤNG");
        lblBalTitle.setFont(UITheme.FONT_SMALL_BOLD);
        lblBalTitle.setForeground(new Color(200, 220, 255));
        lblBalTitle.setAlignmentX(Component.RIGHT_ALIGNMENT);
        rightBox.add(lblBalTitle);

        rightBox.add(Box.createVerticalStrut(4));

        JPanel balRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        balRow.setOpaque(false);
        balRow.setAlignmentX(Component.RIGHT_ALIGNMENT);

        lblBalanceValue = new JLabel(UITheme.MONEY_FORMAT.format(currentBalanceCache));
        lblBalanceValue.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblBalanceValue.setForeground(Color.WHITE);
        balRow.add(lblBalanceValue);

        btnToggleHideBal = new JButton("👁");
        btnToggleHideBal.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
        btnToggleHideBal.setFocusPainted(false);
        btnToggleHideBal.setBorderPainted(false);
        btnToggleHideBal.setContentAreaFilled(false);
        btnToggleHideBal.setForeground(Color.WHITE);
        btnToggleHideBal.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnToggleHideBal.setToolTipText("Ẩn/Hiện số dư");
        btnToggleHideBal.addActionListener(e -> toggleBalanceVisibility());
        balRow.add(btnToggleHideBal);

        rightBox.add(balRow);
        rightBox.add(Box.createVerticalStrut(8));

        btnRefreshBal = new ModernButton("⟳ Cập nhật", ModernButton.Style.OUTLINE);
        btnRefreshBal.setFont(UITheme.FONT_SMALL);
        btnRefreshBal.setCornerRadius(6);
        btnRefreshBal.setAlignmentX(Component.RIGHT_ALIGNMENT);
        btnRefreshBal.addActionListener(e -> refreshBalance());
        rightBox.add(btnRefreshBal);

        card.add(rightBox, BorderLayout.EAST);
        return card;
    }

    /**
     * Tạo Panel form giao dịch chuyển tiền
     */
    private JPanel createTransferFormPanel() {
        RoundedPanel formCard = new RoundedPanel(18, Color.WHITE);
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBorder(new EmptyBorder(20, 24, 20, 24));
        formCard.setShowShadow(true);

        // Header form
        JLabel lblFormTitle = new JLabel("CHUYỂN TIỀN LIÊN TÀI KHOẢN NHANH 24/7");
        lblFormTitle.setFont(UITheme.FONT_TITLE);
        lblFormTitle.setForeground(UITheme.PRIMARY_DARK);
        formCard.add(lblFormTitle);

        JLabel lblFormDesc = new JLabel("Miễn phí chuyển khoản nội bộ. Tiền vào tài khoản thụ hưởng ngay lập tức (Real-time).");
        lblFormDesc.setFont(UITheme.FONT_SMALL);
        lblFormDesc.setForeground(UITheme.TEXT_MUTED);
        formCard.add(lblFormDesc);

        formCard.add(Box.createVerticalStrut(16));

        // 1. TÀI KHOẢN THỤ HƯỞNG & TRA CỨU
        formCard.add(createFieldLabel("Số tài khoản thụ hưởng (*):"));

        JPanel receiverInputRow = new JPanel(new BorderLayout(8, 0));
        receiverInputRow.setOpaque(false);
        receiverInputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        txtReceiverAcc = new ModernTextField("Nhập số tài khoản người nhận (VD: 1002, 1003)...");
        txtReceiverAcc.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                lookupReceiver(false);
            }
        });
        txtReceiverAcc.addActionListener(e -> lookupReceiver(true));

        btnLookupReceiver = new ModernButton("Tra Cứu", ModernButton.Style.PRIMARY);
        btnLookupReceiver.setFont(UITheme.FONT_SMALL_BOLD);
        btnLookupReceiver.setCornerRadius(8);
        btnLookupReceiver.addActionListener(e -> lookupReceiver(true));

        receiverInputRow.add(txtReceiverAcc, BorderLayout.CENTER);
        receiverInputRow.add(btnLookupReceiver, BorderLayout.EAST);
        formCard.add(receiverInputRow);

        formCard.add(Box.createVerticalStrut(6));

        // Banner hiển thị kết quả xác thực người nhận
        pnlReceiverInfo = new RoundedPanel(8, UITheme.BG_INPUT);
        pnlReceiverInfo.setLayout(new BorderLayout());
        pnlReceiverInfo.setBorder(new EmptyBorder(6, 12, 6, 12));
        pnlReceiverInfo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        pnlReceiverInfo.setVisible(false);

        lblReceiverName = new JLabel("");
        lblReceiverName.setFont(UITheme.FONT_BODY_BOLD);
        pnlReceiverInfo.add(lblReceiverName, BorderLayout.CENTER);
        formCard.add(pnlReceiverInfo);

        formCard.add(Box.createVerticalStrut(14));

        // 2. SỐ TIỀN GIAO DỊCH
        formCard.add(createFieldLabel("Số tiền muốn chuyển (VNĐ) (*):"));
        txtAmount = new ModernTextField("Nhập số tiền cần chuyển...");
        txtAmount.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        // Lắng nghe thay đổi số tiền để hiển thị chữ tiếng Việt trực quan
        txtAmount.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateAmountWords(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateAmountWords(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateAmountWords(); }
        });
        formCard.add(txtAmount);

        formCard.add(Box.createVerticalStrut(4));

        lblAmountWords = new JLabel(" ");
        lblAmountWords.setFont(UITheme.FONT_SMALL_BOLD);
        lblAmountWords.setForeground(UITheme.PRIMARY);
        formCard.add(lblAmountWords);

        formCard.add(Box.createVerticalStrut(8));

        // Nút chọn nhanh số tiền (Quick Amount Chips)
        JPanel quickChips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        quickChips.setOpaque(false);
        quickChips.setAlignmentX(Component.LEFT_ALIGNMENT);

        long[] chips = {50000, 100000, 200000, 500000, 1000000, 2000000};
        String[] chipLabels = {"50.000", "100.000", "200.000", "500.000", "1.000.000", "2.000.000"};
        for (int i = 0; i < chips.length; i++) {
            final long val = chips[i];
            ModernButton btnChip = new ModernButton(chipLabels[i], ModernButton.Style.SECONDARY);
            btnChip.setFont(UITheme.FONT_SMALL);
            btnChip.setCornerRadius(6);
            btnChip.addActionListener(e -> txtAmount.setText(String.valueOf(val)));
            quickChips.add(btnChip);
        }

        ModernButton btnAll = new ModernButton("Tất cả số dư", ModernButton.Style.SECONDARY);
        btnAll.setFont(UITheme.FONT_SMALL_BOLD);
        btnAll.setCornerRadius(6);
        btnAll.addActionListener(e -> {
            long allAmt = (long) Math.floor(currentBalanceCache);
            if (allAmt > 0) {
                txtAmount.setText(String.valueOf(allAmt));
            }
        });
        quickChips.add(btnAll);
        formCard.add(quickChips);

        formCard.add(Box.createVerticalStrut(14));

        // 3. NỘI DUNG CHUYỂN KHOẢN
        formCard.add(createFieldLabel("Nội dung chuyển khoản:"));
        txtDescription = new ModernTextField("VD: Chuyen tien");
        txtDescription.setText(currentAccount.getFullName() + " chuyen tien");
        txtDescription.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        formCard.add(txtDescription);

        formCard.add(Box.createVerticalStrut(6));

        // Gợi ý nội dung mẫu
        JPanel descChips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        descChips.setOpaque(false);
        descChips.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] sampleDescs = {"Chuyen tien", "Thanh toan tien nha", "Tra tien an", "Chuc mung sinh nhat"};
        for (String desc : sampleDescs) {
            ModernButton btnDesc = new ModernButton(desc, ModernButton.Style.SECONDARY);
            btnDesc.setFont(UITheme.FONT_SMALL);
            btnDesc.setCornerRadius(6);
            btnDesc.addActionListener(e -> txtDescription.setText(currentAccount.getFullName() + " " + desc.toLowerCase()));
            descChips.add(btnDesc);
        }
        formCard.add(descChips);

        formCard.add(Box.createVerticalStrut(12));

        // Message trạng thái
        lblMessage = new JLabel(" ");
        lblMessage.setFont(UITheme.FONT_SMALL_BOLD);
        lblMessage.setForeground(UITheme.DANGER);
        formCard.add(lblMessage);

        formCard.add(Box.createVerticalStrut(12));

        // Nút bấm xác nhận & Làm mới
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnReset = new ModernButton("Làm Mới Form", ModernButton.Style.SECONDARY);
        btnReset.addActionListener(e -> resetForm());

        btnTransfer = new ModernButton("  XÁC NHẬN CHUYỂN TIỀN  ➔  ", ModernButton.Style.PRIMARY);
        btnTransfer.setFont(UITheme.FONT_BODY_BOLD);
        btnTransfer.addActionListener(e -> executeTransfer());

        actionRow.add(btnReset);
        actionRow.add(btnTransfer);
        formCard.add(actionRow);

        return formCard;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL_BOLD);
        label.setForeground(UITheme.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 2, 4, 0));
        return label;
    }

    private void toggleBalanceVisibility() {
        isBalanceHidden = !isBalanceHidden;
        if (isBalanceHidden) {
            lblBalanceValue.setText("•••••••• VNĐ");
            btnToggleHideBal.setText("🔒");
        } else {
            lblBalanceValue.setText(UITheme.MONEY_FORMAT.format(currentBalanceCache));
            btnToggleHideBal.setText("👁");
        }
    }

    public void refreshBalance() {
        btnRefreshBal.setEnabled(false);
        SwingWorker<Double, Void> worker = new SwingWorker<>() {
            @Override
            protected Double doInBackground() throws Exception {
                return bankService.getBalance(currentAccount.getAccountNumber());
            }

            @Override
            protected void done() {
                btnRefreshBal.setEnabled(true);
                try {
                    double bal = get();
                    if (bal >= 0) {
                        currentBalanceCache = bal;
                        currentAccount.setBalance(bal);
                        if (!isBalanceHidden) {
                            lblBalanceValue.setText(UITheme.MONEY_FORMAT.format(bal));
                        }
                    }
                } catch (Exception ex) {
                    lblBalanceValue.setText("Lỗi kết nối");
                }
            }
        };
        worker.execute();
    }

    private void updateAmountWords() {
        String raw = txtAmount.getText().trim().replace(",", "").replace(".", "");
        if (raw.isEmpty()) {
            lblAmountWords.setText(" ");
            return;
        }
        try {
            long amt = Long.parseLong(raw);
            if (amt > 0) {
                lblAmountWords.setForeground(UITheme.PRIMARY);
                lblAmountWords.setText("✎ " + UITheme.MONEY_FORMAT.format(amt) + " (" + UITheme.toVietnameseCurrencyWords(amt) + ")");
            } else {
                lblAmountWords.setText(" ");
            }
        } catch (NumberFormatException e) {
            lblAmountWords.setForeground(UITheme.DANGER);
            lblAmountWords.setText("⚠ Vui lòng chỉ nhập các chữ số!");
        }
    }

    /**
     * Tra cứu người nhận tự động hoặc khi bấm nút Tra cứu
     */
    private void lookupReceiver(boolean showToastOnError) {
        String toAcc = txtReceiverAcc.getText().trim();
        if (toAcc.isEmpty()) {
            pnlReceiverInfo.setVisible(false);
            verifiedReceiverName = null;
            return;
        }

        if (toAcc.equals(currentAccount.getAccountNumber())) {
            pnlReceiverInfo.setBackgroundColor(UITheme.DANGER_LIGHT);
            lblReceiverName.setForeground(UITheme.DANGER_DARK);
            lblReceiverName.setText("✕ Không thể chuyển tiền cho chính tài khoản của bạn!");
            pnlReceiverInfo.setVisible(true);
            verifiedReceiverName = null;
            return;
        }

        btnLookupReceiver.setEnabled(false);
        SwingWorker<Account, Void> worker = new SwingWorker<>() {
            @Override
            protected Account doInBackground() throws Exception {
                return bankService.getAccountByNumber(toAcc);
            }

            @Override
            protected void done() {
                btnLookupReceiver.setEnabled(true);
                try {
                    Account target = get();
                    if (target != null) {
                        if ("LOCKED".equalsIgnoreCase(target.getStatus())) {
                            pnlReceiverInfo.setBackgroundColor(UITheme.DANGER_LIGHT);
                            lblReceiverName.setForeground(UITheme.DANGER_DARK);
                            lblReceiverName.setText("✕ Tài khoản này đang bị KHÓA bởi Quản trị viên!");
                            verifiedReceiverName = null;
                        } else {
                            pnlReceiverInfo.setBackgroundColor(UITheme.SUCCESS_LIGHT);
                            lblReceiverName.setForeground(UITheme.SUCCESS_DARK);
                            lblReceiverName.setText("✓ Người thụ hưởng: " + target.getFullName().toUpperCase() + " (Đang hoạt động)");
                            verifiedReceiverName = target.getFullName();
                        }
                    } else {
                        pnlReceiverInfo.setBackgroundColor(UITheme.DANGER_LIGHT);
                        lblReceiverName.setForeground(UITheme.DANGER_DARK);
                        lblReceiverName.setText("✕ Số tài khoản không tồn tại trên hệ thống!");
                        verifiedReceiverName = null;
                        if (showToastOnError) {
                            JOptionPane.showMessageDialog(TransferForm.this,
                                    "Không tìm thấy số tài khoản: " + toAcc,
                                    "Tra Cứu Thất Bại", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                    pnlReceiverInfo.setVisible(true);
                } catch (Exception ex) {
                    pnlReceiverInfo.setVisible(false);
                    verifiedReceiverName = null;
                }
            }
        };
        worker.execute();
    }

    private void resetForm() {
        txtReceiverAcc.setText("");
        pnlReceiverInfo.setVisible(false);
        verifiedReceiverName = null;
        txtAmount.setText("");
        lblAmountWords.setText(" ");
        txtDescription.setText(currentAccount.getFullName() + " chuyen tien");
        lblMessage.setText(" ");
    }

    private void executeTransfer() {
        String toAcc = txtReceiverAcc.getText().trim();
        String amountText = txtAmount.getText().trim().replace(",", "").replace(".", "");
        String desc = txtDescription.getText().trim();

        // 1. Validation input chặt chẽ
        if (toAcc.isEmpty()) {
            lblMessage.setForeground(UITheme.DANGER);
            lblMessage.setText("Vui lòng nhập số tài khoản người nhận!");
            txtReceiverAcc.requestFocus();
            return;
        }

        if (toAcc.equals(currentAccount.getAccountNumber())) {
            lblMessage.setForeground(UITheme.DANGER);
            lblMessage.setText("Không thể tự chuyển tiền cho chính mình!");
            JOptionPane.showMessageDialog(this,
                    "Không thể tự chuyển tiền cho chính tài khoản của bạn!",
                    "Lỗi Giao Dịch", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountText);
            if (amount <= 0) {
                lblMessage.setForeground(UITheme.DANGER);
                lblMessage.setText("Số tiền chuyển phải lớn hơn 0 VNĐ!");
                txtAmount.requestFocus();
                return;
            }
        } catch (NumberFormatException ex) {
            lblMessage.setForeground(UITheme.DANGER);
            lblMessage.setText("Số tiền không hợp lệ! Vui lòng chỉ nhập số.");
            txtAmount.requestFocus();
            return;
        }

        if (amount > currentBalanceCache) {
            lblMessage.setForeground(UITheme.DANGER);
            lblMessage.setText("Số dư khả dụng không đủ để thực hiện giao dịch này!");
            JOptionPane.showMessageDialog(this,
                    "Số dư khả dụng hiện tại (" + UITheme.MONEY_FORMAT.format(currentBalanceCache) + ")\n" +
                    "không đủ để chuyển số tiền " + UITheme.MONEY_FORMAT.format(amount) + "!",
                    "Không Đủ Số Dư", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2. Hiển thị Dialog Xác nhận thông tin giao dịch hiện đại
        String targetNameDisplay = (verifiedReceiverName != null) ? verifiedReceiverName.toUpperCase() : "Người nhận (" + toAcc + ")";
        int confirm = JOptionPane.showConfirmDialog(this,
                "XÁC NHẬN GIAO DỊCH CHUYỂN TIỀN\n\n" +
                "• Người gửi: " + currentAccount.getFullName() + " (" + currentAccount.getAccountNumber() + ")\n" +
                "• Người nhận: " + targetNameDisplay + "\n" +
                "• Số tài khoản nhận: " + toAcc + "\n" +
                "• Số tiền: " + UITheme.MONEY_FORMAT.format(amount) + "\n" +
                "• Bằng chữ: " + UITheme.toVietnameseCurrencyWords(Math.round(amount)) + "\n" +
                "• Phí giao dịch: 0 VNĐ (Miễn phí 24/7)\n" +
                "• Nội dung: " + desc + "\n\n" +
                "Bạn có chắc chắn muốn thực hiện giao dịch này?",
                "Xác Nhận Chuyển Tiền",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // 3. Khóa nút bấm để chống Double-Click và hiển thị trạng thái đang xử lý
        btnTransfer.setEnabled(false);
        btnReset.setEnabled(false);
        btnRefreshBal.setEnabled(false);
        lblMessage.setForeground(UITheme.PRIMARY);
        lblMessage.setText("Đang xử lý giao dịch ACID an toàn trên máy chủ RMI...");

        // 4. Luồng xử lý ngầm SwingWorker tránh đóng băng giao diện
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private String error = null;

            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    return bankService.transfer(currentAccount.getAccountNumber(), toAcc, amount, desc);
                } catch (RemoteException ex) {
                    error = ex.getMessage();
                    return false;
                }
            }

            @Override
            protected void done() {
                btnTransfer.setEnabled(true);
                btnReset.setEnabled(true);
                btnRefreshBal.setEnabled(true);

                try {
                    boolean success = get();
                    if (success) {
                        lblMessage.setForeground(UITheme.SUCCESS);
                        lblMessage.setText("Giao dịch chuyển tiền thành công!");

                        // Cập nhật lại số dư mới
                        refreshBalance();
                        if (onTransferSuccessCallback != null) {
                            onTransferSuccessCallback.run();
                        }

                        // Hiển thị Biên lai điện tử hiện đại
                        Window parentWindow = SwingUtilities.getWindowAncestor(TransferForm.this);
                        TransactionReceiptDialog receipt = new TransactionReceiptDialog(
                                parentWindow,
                                currentAccount.getAccountNumber(),
                                currentAccount.getFullName(),
                                toAcc,
                                verifiedReceiverName,
                                amount,
                                desc
                        );
                        receipt.setVisible(true);

                        // Reset form sau khi hoàn tất
                        resetForm();

                    } else {
                        lblMessage.setForeground(UITheme.DANGER);
                        if (error != null) {
                            lblMessage.setText("Lỗi kết nối mạng: " + error);
                            JOptionPane.showMessageDialog(TransferForm.this,
                                    "Không thể kết nối đến máy chủ: " + error,
                                    "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                        } else {
                            lblMessage.setText("Chuyển tiền thất bại: Tài khoản nhận không tồn tại, bị khóa hoặc số dư không đủ!");
                            JOptionPane.showMessageDialog(TransferForm.this,
                                    "GIAO DỊCH KHÔNG THÀNH CÔNG!\n\n" +
                                    "Nguyên nhân có thể do:\n" +
                                    "• Số tài khoản thụ hưởng không tồn tại trên hệ thống\n" +
                                    "• Tài khoản thụ hưởng đang bị KHÓA\n" +
                                    "• Số dư khả dụng không đủ tại thời điểm trừ tiền",
                                    "Giao Dịch Thất Bại", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    lblMessage.setForeground(UITheme.DANGER);
                    lblMessage.setText("Lỗi ngoại lệ trong quá trình xử lý giao dịch!");
                    ex.printStackTrace();
                }
            }
        };

        worker.execute();
    }
}
