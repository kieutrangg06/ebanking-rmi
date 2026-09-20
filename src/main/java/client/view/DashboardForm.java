package client.view;

import client.callback.ClientCallbackImpl;
import common.models.Account;
import common.models.Bill;
import common.models.Saving;
import common.models.Transaction;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.rmi.RemoteException;
import java.util.List;

public class DashboardForm extends JFrame {
    private final IBankService bankService;
    private final Account currentAccount;
    private JLabel lblBalance;

    public DashboardForm(IBankService bankService, Account account) {
        this.bankService = bankService;
        this.currentAccount = account;

        setTitle("e-Banking RMI - Xin chào: " + account.getFullName() + " (STK: " + account.getAccountNumber() + ")");
        setSize(750, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Header hiển thị số dư
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        topPanel.setBackground(new Color(230, 240, 250));
        JLabel lblUser = new JLabel("Chủ TK: " + account.getFullName() + " | STK: " + account.getAccountNumber());
        lblBalance = new JLabel();
        lblBalance.setFont(new Font("Arial", Font.BOLD, 14));
        lblBalance.setForeground(new Color(0, 128, 0));
        updateBalanceLabel();

        topPanel.add(lblUser);
        topPanel.add(lblBalance);
        add(topPanel, BorderLayout.NORTH);

        // Tab chứa các phân hệ của 3 thành viên
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("1. Chuyển Khoản (Dev 1)", createTransferPanel());
        tabbedPane.addTab("2. Thanh Toán Hóa Đơn & Sao Kê (Dev 2)", createBillAndHistoryPanel());
        tabbedPane.addTab("3. Tiết Kiệm & Admin (Dev 3)", createSavingAndAdminPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    public void updateBalanceLabel() {
        try {
            double bal = bankService.getBalance(currentAccount.getAccountNumber());
            currentAccount.setBalance(bal);
            lblBalance.setText("Số Dư: " + String.format("%,.0f VNĐ", bal));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    // --- TAB 1: NGƯỜI 1 (CHUYỂN KHOẢN) ---
    private JPanel createTransferPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        JTextField txtToAcc = new JTextField();
        JTextField txtAmount = new JTextField();
        JTextField txtDesc = new JTextField("Chuyen tien");
        JButton btnSend = new JButton("Xác Nhận Chuyển Tiền");

        panel.add(new JLabel("Số tài khoản nhận:")); panel.add(txtToAcc);
        panel.add(new JLabel("Số tiền (VNĐ):")); panel.add(txtAmount);
        panel.add(new JLabel("Nội dung:")); panel.add(txtDesc);
        panel.add(new JLabel("")); panel.add(btnSend);

        btnSend.addActionListener(e -> {
            try {
                String toAcc = txtToAcc.getText().trim();
                double amount = Double.parseDouble(txtAmount.getText().trim());
                String desc = txtDesc.getText().trim();

                boolean ok = bankService.transfer(currentAccount.getAccountNumber(), toAcc, amount, desc);
                if (ok) {
                    JOptionPane.showMessageDialog(this, "Chuyển tiền thành công!");
                    updateBalanceLabel();
                    txtAmount.setText("");
                } else {
                    JOptionPane.showMessageDialog(this, "Thất bại: Số dư không đủ hoặc số tài khoản nhận sai!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Dữ liệu nhập không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
        return panel;
    }

    // --- TAB 2: NGƯỜI 2 (HÓA ĐƠN & SAO KÊ) ---
    private JPanel createBillAndHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Nửa trên: Hóa đơn
        JPanel billBox = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField txtBillCode = new JTextField(12);
        JButton btnQuery = new JButton("Tra cứu");
        JButton btnPay = new JButton("Thanh toán");
        JLabel lblBillInfo = new JLabel("Chưa tra cứu hóa đơn");

        billBox.add(new JLabel("Mã HĐ:"));
        billBox.add(txtBillCode);
        billBox.add(btnQuery);
        billBox.add(btnPay);
        billBox.add(lblBillInfo);

        btnQuery.addActionListener(e -> {
            try {
                Bill bill = bankService.queryBill(txtBillCode.getText().trim());
                if (bill != null) {
                    lblBillInfo.setText(bill.getServiceType() + " - Tiền: " + String.format("%,.0f VNĐ", bill.getAmount()) + " [" + bill.getStatus() + "]");
                } else {
                    lblBillInfo.setText("Không tìm thấy mã hóa đơn!");
                }
            } catch (RemoteException ex) { ex.printStackTrace(); }
        });

        btnPay.addActionListener(e -> {
            try {
                boolean ok = bankService.payBill(currentAccount.getAccountNumber(), txtBillCode.getText().trim());
                if (ok) {
                    JOptionPane.showMessageDialog(this, "Thanh toán hóa đơn thành công!");
                    updateBalanceLabel();
                    lblBillInfo.setText("Đã thanh toán");
                } else {
                    JOptionPane.showMessageDialog(this, "Thanh toán thất bại: Không đủ tiền hoặc hóa đơn đã trả!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (RemoteException ex) { ex.printStackTrace(); }
        });

        // Nửa dưới: Bảng sao kê
        DefaultTableModel model = new DefaultTableModel(new String[]{"Thời Gian", "Loại GD", "Từ TK", "Đến TK", "Số Tiền", "Mô Tả"}, 0);
        JTable table = new JTable(model);
        JButton btnRefreshHistory = new JButton("Tải lại lịch sử GD (Sao kê)");

        btnRefreshHistory.addActionListener(e -> {
            try {
                model.setRowCount(0);
                List<Transaction> list = bankService.getTransactionHistory(currentAccount.getAccountNumber());
                for (Transaction t : list) {
                    model.addRow(new Object[]{t.getCreatedAt(), t.getTransactionType(), t.getFromAccount(), t.getToAccount(), String.format("%,.0f", t.getAmount()), t.getDescription()});
                }
            } catch (RemoteException ex) { ex.printStackTrace(); }
        });

        JPanel historyBox = new JPanel(new BorderLayout());
        historyBox.add(btnRefreshHistory, BorderLayout.NORTH);
        historyBox.add(new JScrollPane(table), BorderLayout.CENTER);

        panel.add(billBox, BorderLayout.NORTH);
        panel.add(historyBox, BorderLayout.CENTER);
        return panel;
    }

    // --- TAB 3: NGƯỜI 3 (TIẾT KIỆM & QUẢN TRỊ ADMIN) ---
    private JPanel createSavingAndAdminPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Khối 1: Gửi tiết kiệm
        JPanel savingBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        savingBox.setBorder(BorderFactory.createTitledBorder("Mở Sổ Tiết Kiệm Nhanh"));
        JTextField txtSavAmount = new JTextField("500000", 8);
        JButton btnOpenSaving = new JButton("Gửi Tiết Kiệm (Lãi 5%/chu kỳ)");
        JButton btnCheckSavings = new JButton("Xem Sổ Hiện Có");

        savingBox.add(new JLabel("Tiền gửi:"));
        savingBox.add(txtSavAmount);
        savingBox.add(btnOpenSaving);
        savingBox.add(btnCheckSavings);

        btnOpenSaving.addActionListener(e -> {
            try {
                double amt = Double.parseDouble(txtSavAmount.getText().trim());
                boolean ok = bankService.openSaving(currentAccount.getAccountNumber(), amt, 5.0, 15);
                if (ok) {
                    JOptionPane.showMessageDialog(this, "Mở sổ thành công! Lãi sẽ được cộng tự động mỗi 15 giây.");
                    updateBalanceLabel();
                } else {
                    JOptionPane.showMessageDialog(this, "Số dư không đủ!");
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        });

        btnCheckSavings.addActionListener(e -> {
            try {
                List<Saving> list = bankService.getSavingsByAccount(currentAccount.getAccountNumber());
                StringBuilder sb = new StringBuilder("DANH SÁCH SỔ TIẾT KIỆM:\n");
                for (Saving s : list) {
                    sb.append("Sổ #").append(s.getId()).append(" | Gốc: ").append(String.format("%,.0f", s.getDepositAmount()))
                      .append(" | Lãi tích luỹ: ").append(String.format("%,.0f", s.getAccumulatedInterest()))
                      .append(" | TT: ").append(s.getStatus()).append("\n");
                }
                JOptionPane.showMessageDialog(this, sb.toString());
            } catch (RemoteException ex) { ex.printStackTrace(); }
        });

        // Khối 2: Giám sát Admin
        JPanel adminBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        adminBox.setBorder(BorderFactory.createTitledBorder("Admin / Giám Sát Kết Nối Mạng"));
        JButton btnViewOnline = new JButton("Quét User Online");
        JTextField txtLockAcc = new JTextField(6);
        JButton btnLock = new JButton("Khóa & Kick Tài Khoản");

        adminBox.add(btnViewOnline);
        adminBox.add(new JLabel("STK cần khóa:"));
        adminBox.add(txtLockAcc);
        adminBox.add(btnLock);

        btnViewOnline.addActionListener(e -> {
            try {
                List<String> onlines = bankService.getOnlineUsers();
                JOptionPane.showMessageDialog(this, "Các tài khoản đang online:\n" + String.join(", ", onlines));
            } catch (RemoteException ex) { ex.printStackTrace(); }
        });

        btnLock.addActionListener(e -> {
            try {
                String targetAcc = txtLockAcc.getText().trim();
                boolean ok = bankService.lockAccount(targetAcc, "Phat hien nghi van gian lan");
                if (ok) {
                    JOptionPane.showMessageDialog(this, "Đã khóa và ngắt kết nối tài khoản " + targetAcc);
                } else {
                    JOptionPane.showMessageDialog(this, "Khóa thất bại hoặc tài khoản không tồn tại!");
                }
            } catch (RemoteException ex) { ex.printStackTrace(); }
        });

        panel.add(savingBox);
        panel.add(adminBox);
        return panel;
    }
}