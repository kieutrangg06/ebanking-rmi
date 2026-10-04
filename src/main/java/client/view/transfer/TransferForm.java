package client.view.transfer;

import common.models.Account;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.rmi.RemoteException;
import java.text.DecimalFormat;

public class TransferForm extends JPanel {
    private final IBankService bankService;
    private final Account currentAccount;
    private final Runnable onTransferSuccessCallback;

    private JTextField txtSenderAcc;
    private JLabel lblBalanceValue;
    private JTextField txtReceiverAcc;
    private JTextField txtAmount;
    private JTextField txtDescription;
    private JButton btnTransfer;
    private JButton btnRefreshBal;
    private JLabel lblMessage;

    private final DecimalFormat moneyFormat = new DecimalFormat("#,### VNĐ");

    public TransferForm(IBankService bankService, Account currentAccount, Runnable onTransferSuccessCallback) {
        this.bankService = bankService;
        this.currentAccount = currentAccount;
        this.onTransferSuccessCallback = onTransferSuccessCallback;
        initComponents();
        refreshBalance();
    }

    private void initComponents() {
        setLayout(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 20, 15, 20));
        setBackground(Color.WHITE);

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        JLabel lblTitle = new JLabel("DỊCH VỤ CHUYỂN KHOẢN LIÊN TÀI KHOẢN");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(new Color(24, 76, 120));
        headerPanel.add(lblTitle, BorderLayout.NORTH);

        // Balance Box
        JPanel balancePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        balancePanel.setBackground(new Color(240, 248, 255));
        balancePanel.setBorder(BorderFactory.createLineBorder(new Color(180, 210, 240), 1));

        JLabel lblBalTitle = new JLabel("Số dư khả dụng:");
        lblBalTitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        lblBalanceValue = new JLabel("0 VNĐ");
        lblBalanceValue.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblBalanceValue.setForeground(new Color(0, 128, 0));

        btnRefreshBal = new JButton("Làm mới");
        btnRefreshBal.setFocusPainted(false);
        btnRefreshBal.setBackground(new Color(230, 240, 250));
        btnRefreshBal.addActionListener(e -> refreshBalance());

        balancePanel.add(lblBalTitle);
        balancePanel.add(lblBalanceValue);
        balancePanel.add(btnRefreshBal);

        headerPanel.add(balancePanel, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // Form fields inside a styled card
        JPanel formCard = new JPanel(new GridBagLayout());
        formCard.setBackground(Color.WHITE);
        formCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 230)),
                " Thông Tin Giao Dịch Chuyển Tiền ",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 13),
                new Color(70, 80, 95)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Sender Account
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        formCard.add(new JLabel("Tài khoản nguồn:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        txtSenderAcc = new JTextField(currentAccount.getAccountNumber() + " (" + currentAccount.getFullName() + ")");
        txtSenderAcc.setEditable(false);
        txtSenderAcc.setBackground(new Color(245, 245, 245));
        formCard.add(txtSenderAcc, gbc);

        // Row 1: Receiver Account
        gbc.gridx = 0; gbc.gridy = 1;
        formCard.add(new JLabel("Tài khoản thụ hưởng (*):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        txtReceiverAcc = new JTextField();
        txtReceiverAcc.setToolTipText("Nhập số tài khoản người nhận (VD: 1002, 1003)");
        formCard.add(txtReceiverAcc, gbc);

        // Row 2: Amount
        gbc.gridx = 0; gbc.gridy = 2;
        formCard.add(new JLabel("Số tiền chuyển (VNĐ) (*):"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        txtAmount = new JTextField();
        txtAmount.setToolTipText("Số tiền muốn chuyển lớn hơn 0");
        formCard.add(txtAmount, gbc);

        // Quick amount buttons
        JPanel quickAmountPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        quickAmountPanel.setBackground(Color.WHITE);
        String[] amounts = {"50,000", "100,000", "200,000", "500,000", "1,000,000"};
        long[] amountVals = {50000, 100000, 200000, 500000, 1000000};
        for (int i = 0; i < amounts.length; i++) {
            JButton btnQuick = new JButton(amounts[i]);
            btnQuick.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            btnQuick.setMargin(new Insets(2, 6, 2, 6));
            final long val = amountVals[i];
            btnQuick.addActionListener(e -> txtAmount.setText(String.valueOf(val)));
            quickAmountPanel.add(btnQuick);
        }
        gbc.gridx = 1; gbc.gridy = 3;
        formCard.add(quickAmountPanel, gbc);

        // Row 4: Description
        gbc.gridx = 0; gbc.gridy = 4;
        formCard.add(new JLabel("Nội dung chuyển tiền:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4;
        txtDescription = new JTextField("Chuyen tien tu " + currentAccount.getFullName());
        formCard.add(txtDescription, gbc);

        // Row 5: Message status
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        lblMessage = new JLabel(" ");
        lblMessage.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblMessage.setForeground(Color.RED);
        formCard.add(lblMessage, gbc);

        add(formCard, BorderLayout.CENTER);

        // Footer Button Panel
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        footerPanel.setBackground(Color.WHITE);

        JButton btnReset = new JButton("Nhập Lại");
        btnReset.setBackground(new Color(240, 240, 240));
        btnReset.setFocusPainted(false);
        btnReset.addActionListener(e -> {
            txtReceiverAcc.setText("");
            txtAmount.setText("");
            txtDescription.setText("Chuyen tien");
            lblMessage.setText(" ");
        });

        btnTransfer = new JButton("  XÁC NHẬN CHUYỂN TIỀN  ");
        btnTransfer.setBackground(new Color(24, 119, 242));
        btnTransfer.setForeground(Color.WHITE);
        btnTransfer.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnTransfer.setFocusPainted(false);
        btnTransfer.addActionListener(e -> executeTransfer());

        footerPanel.add(btnReset);
        footerPanel.add(btnTransfer);
        add(footerPanel, BorderLayout.SOUTH);
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
                        currentAccount.setBalance(bal);
                        lblBalanceValue.setText(moneyFormat.format(bal));
                    }
                } catch (Exception ex) {
                    lblBalanceValue.setText("Lỗi kết nối");
                }
            }
        };
        worker.execute();
    }

    private void executeTransfer() {
        String toAcc = txtReceiverAcc.getText().trim();
        String amountText = txtAmount.getText().trim().replace(",", "").replace(".", "");
        String desc = txtDescription.getText().trim();

        // 1. Validation kiểm tra input
        if (toAcc.isEmpty()) {
            lblMessage.setForeground(Color.RED);
            lblMessage.setText("Vui lòng nhập số tài khoản người nhận!");
            txtReceiverAcc.requestFocus();
            return;
        }

        if (toAcc.equals(currentAccount.getAccountNumber())) {
            lblMessage.setForeground(Color.RED);
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
                lblMessage.setForeground(Color.RED);
                lblMessage.setText("Số tiền chuyển phải lớn hơn 0 VNĐ!");
                return;
            }
        } catch (NumberFormatException ex) {
            lblMessage.setForeground(Color.RED);
            lblMessage.setText("Số tiền không hợp lệ! Vui lòng chỉ nhập số.");
            txtAmount.requestFocus();
            return;
        }

        if (amount > currentAccount.getBalance()) {
            lblMessage.setForeground(Color.RED);
            lblMessage.setText("Số dư khả dụng không đủ để thực hiện giao dịch!");
            JOptionPane.showMessageDialog(this,
                    "Số dư khả dụng (" + moneyFormat.format(currentAccount.getBalance()) + ") không đủ để chuyển " + moneyFormat.format(amount) + "!",
                    "Không Đủ Số Dư", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2. Xác nhận trước khi chuyển tiền
        int confirm = JOptionPane.showConfirmDialog(this,
                "Xác nhận chuyển số tiền: " + moneyFormat.format(amount) + "\nĐến tài khoản: " + toAcc + "\nNội dung: " + desc,
                "Xác Nhận Chuyển Tiền",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // 3. Chống Double-click: Disable nút chuyển tiền ngay lập tức
        btnTransfer.setEnabled(false);
        btnRefreshBal.setEnabled(false);
        lblMessage.setForeground(Color.BLUE);
        lblMessage.setText("Đang xử lý giao dịch qua máy chủ RMI...");

        // 4. Gọi RMI bất đồng bộ trên SwingWorker
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
                btnRefreshBal.setEnabled(true);
                try {
                    boolean success = get();
                    if (success) {
                        lblMessage.setForeground(new Color(0, 150, 0));
                        lblMessage.setText("Giao dịch chuyển tiền thành công!");
                        txtAmount.setText("");
                        refreshBalance();

                        if (onTransferSuccessCallback != null) {
                            onTransferSuccessCallback.run();
                        }

                        JOptionPane.showMessageDialog(TransferForm.this,
                                "GIAO DỊCH THÀNH CÔNG!\n" +
                                "Đã chuyển: " + moneyFormat.format(amount) + "\n" +
                                "Đến tài khoản: " + toAcc + "\n" +
                                "Nội dung: " + desc,
                                "Chuyển Tiền Thành Công",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        lblMessage.setForeground(Color.RED);
                        if (error != null) {
                            lblMessage.setText("Lỗi mạng RMI: " + error);
                            JOptionPane.showMessageDialog(TransferForm.this,
                                    "Không thể kết nối đến máy chủ: " + error,
                                    "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                        } else {
                            lblMessage.setText("Chuyển tiền thất bại: Tài khoản nhận không tồn tại, bị khóa hoặc không đủ số dư!");
                            JOptionPane.showMessageDialog(TransferForm.this,
                                    "Chuyển tiền thất bại!\nNguyên nhân có thể do:\n- Số tài khoản thụ hưởng không tồn tại\n- Tài khoản thụ hưởng đang bị KHÓA\n- Số dư không đủ",
                                    "Giao Dịch Thất Bại", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } catch (Exception ex) {
                    lblMessage.setForeground(Color.RED);
                    lblMessage.setText("Lỗi hệ thống khi chuyển tiền!");
                    ex.printStackTrace();
                }
            }
        };

        worker.execute();
    }
}
