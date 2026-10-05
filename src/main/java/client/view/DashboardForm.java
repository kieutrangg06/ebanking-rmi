package client.view;

import client.callback.ClientCallbackImpl;
import client.view.admin.AdminDashboardForm;
import client.view.saving.SavingForm;
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
    private SavingForm savingForm;

    public DashboardForm(IBankService bankService, Account account) {
        this.bankService = bankService;
        this.currentAccount = account;

        setTitle("e-Banking RMI - Xin chào: " + account.getFullName() + " (STK: " + account.getAccountNumber() + ")");
        setSize(800, 560);
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

        if ("admin".equalsIgnoreCase(account.getUsername()) || "9999".equals(account.getAccountNumber())) {
            JButton btnAdmin = new JButton("🛡️ Admin Dashboard");
            btnAdmin.setBackground(new Color(231, 76, 60));
            btnAdmin.setForeground(Color.WHITE);
            btnAdmin.setFont(new Font("Arial", Font.BOLD, 12));
            btnAdmin.setFocusPainted(false);
            btnAdmin.addActionListener(e -> new AdminDashboardForm(bankService, account).setVisible(true));
            topPanel.add(btnAdmin);
        }

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
            if (savingForm != null) {
                savingForm.loadSavingsData();
            }
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
        JPanel container = new JPanel(new BorderLayout(8, 8));

        // Nhúng SavingForm (Mở sổ, theo dõi tiền lãi tự động nhảy, tất toán)
        savingForm = new SavingForm(bankService, currentAccount, this::updateBalanceLabel);
        container.add(savingForm, BorderLayout.CENTER);

        // Thanh công cụ mở Admin Dashboard chuyên nghiệp
        JPanel adminBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 8));
        adminBar.setBackground(new Color(235, 240, 248));
        adminBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 210, 225)));

        JLabel lblAdminHint = new JLabel("🛡️ Quyền quản trị & giám sát hệ thống:");
        lblAdminHint.setFont(new Font("Arial", Font.ITALIC, 12));

        JButton btnOpenAdmin = new JButton("Mở Bảng Điều Khiển Admin (Giám sát & Kick)");
        btnOpenAdmin.setBackground(new Color(41, 128, 185));
        btnOpenAdmin.setForeground(Color.WHITE);
        btnOpenAdmin.setFont(new Font("Arial", Font.BOLD, 12));
        btnOpenAdmin.setFocusPainted(false);
        btnOpenAdmin.addActionListener(e -> new AdminDashboardForm(bankService, currentAccount).setVisible(true));

        adminBar.add(lblAdminHint);
        adminBar.add(btnOpenAdmin);
        container.add(adminBar, BorderLayout.SOUTH);

        return container;
    }
}