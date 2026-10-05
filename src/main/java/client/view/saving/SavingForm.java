package client.view.saving;

import common.models.Account;
import common.models.Saving;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.rmi.RemoteException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Giao diện Quản lý Sổ Tiết Kiệm (Dev 3 - Tiết kiệm & Admin).
 * Cho phép khách hàng mở sổ tiết kiệm trực tuyến, theo dõi tiền lãi tích lũy tự động nhảy theo thời gian thực,
 * và tất toán sổ tiết kiệm về tài khoản chính.
 */
public class SavingForm extends JPanel {

    private final IBankService bankService;
    private final Account currentAccount;
    private final Runnable onBalanceChangedCallback;

    private JTable tableSavings;
    private DefaultTableModel tableModel;
    private JTextField txtDepositAmount;
    private JComboBox<TermOption> cbTerms;
    private JLabel lblTotalSavingBalance;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0 VNĐ");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static class TermOption {
        private final String label;
        private final int seconds;
        private final double rate;

        public TermOption(String label, int seconds, double rate) {
            this.label = label;
            this.seconds = seconds;
            this.rate = rate;
        }

        public int getSeconds() { return seconds; }
        public double getRate() { return rate; }

        @Override
        public String toString() {
            return label;
        }
    }

    public SavingForm(IBankService bankService, Account currentAccount, Runnable onBalanceChangedCallback) {
        this.bankService = bankService;
        this.currentAccount = currentAccount;
        this.onBalanceChangedCallback = onBalanceChangedCallback;

        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setBackground(new Color(248, 249, 250));

        initUI();
        loadSavingsData();
    }

    private void initUI() {
        // --- PHẦN 1: PANEL TRÊN - MỞ SỔ TIẾT KIỆM MỚI ---
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBackground(Color.WHITE);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel lblTitle = new JLabel("💰 GỬI TIẾT KIỆM TRỰC TUYẾN (SINH LÃI TỰ ĐỘNG)");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 15));
        lblTitle.setForeground(new Color(15, 82, 186));
        topPanel.add(lblTitle, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        inputPanel.setOpaque(false);

        inputPanel.add(new JLabel("Số tiền gửi:"));
        txtDepositAmount = new JTextField("500000", 10);
        txtDepositAmount.setFont(new Font("Arial", Font.PLAIN, 13));
        inputPanel.add(txtDepositAmount);

        inputPanel.add(new JLabel("Gói kỳ hạn:"));
        cbTerms = new JComboBox<>(new TermOption[]{
                new TermOption("Siêu ngắn: 15s (Lãi 5%/kỳ)", 15, 5.0),
                new TermOption("Linh hoạt: 30s (Lãi 7%/kỳ)", 30, 7.0),
                new TermOption("Định kỳ: 60s (Lãi 10%/kỳ)", 60, 10.0)
        });
        inputPanel.add(cbTerms);

        JButton btnOpen = new JButton("Mở Sổ Tiết Kiệm");
        btnOpen.setBackground(new Color(39, 174, 96));
        btnOpen.setForeground(Color.WHITE);
        btnOpen.setFont(new Font("Arial", Font.BOLD, 13));
        btnOpen.setFocusPainted(false);
        inputPanel.add(btnOpen);

        topPanel.add(inputPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // --- PHẦN 2: BẢNG DANH SÁCH CÁC SỔ TIẾT KIỆM ĐANG CÓ ---
        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.setBackground(Color.WHITE);
        centerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));

        JPanel tableHeaderPanel = new JPanel(new BorderLayout());
        tableHeaderPanel.setOpaque(false);
        JLabel lblListTitle = new JLabel("Danh Sách Sổ Tiết Kiệm Của Bạn (Lãi được cộng ngầm bởi Thread nền)");
        lblListTitle.setFont(new Font("Arial", Font.BOLD, 13));
        lblTotalSavingBalance = new JLabel("Tổng tiết kiệm: 0 VNĐ");
        lblTotalSavingBalance.setFont(new Font("Arial", Font.BOLD, 13));
        lblTotalSavingBalance.setForeground(new Color(41, 128, 185));

        tableHeaderPanel.add(lblListTitle, BorderLayout.WEST);
        tableHeaderPanel.add(lblTotalSavingBalance, BorderLayout.EAST);
        centerPanel.add(tableHeaderPanel, BorderLayout.NORTH);

        String[] columns = {"Mã Sổ", "Số Tiền Gốc", "Lãi Suất", "Chu Kỳ", "Lãi Tích Lũy", "Tổng Nhận Được", "Trạng Thái", "Ngày Mở"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Không cho sửa cell
            }
        };

        tableSavings = new JTable(tableModel);
        tableSavings.setRowHeight(26);
        tableSavings.setFont(new Font("Arial", Font.PLAIN, 12));
        tableSavings.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        tableSavings.getTableHeader().setBackground(new Color(236, 240, 241));
        tableSavings.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Canh lề
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tableSavings.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tableSavings.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        tableSavings.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        tableSavings.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
        tableSavings.getColumnModel().getColumn(7).setCellRenderer(centerRenderer);

        JScrollPane scrollPane = new JScrollPane(tableSavings);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // --- PHẦN 3: NÚT THAO TÁC Ở DƯỚI (TẤT TOÁN, LÀM MỚI) ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        bottomPanel.setOpaque(false);

        JButton btnRefresh = new JButton("🔄 Làm Mới");
        btnRefresh.setFont(new Font("Arial", Font.PLAIN, 13));
        bottomPanel.add(btnRefresh);

        JButton btnSettle = new JButton("💎 Tất Toán Sổ Tiết Kiệm (Rút Gốc + Lãi)");
        btnSettle.setBackground(new Color(230, 126, 34));
        btnSettle.setForeground(Color.WHITE);
        btnSettle.setFont(new Font("Arial", Font.BOLD, 13));
        btnSettle.setFocusPainted(false);
        bottomPanel.add(btnSettle);

        add(bottomPanel, BorderLayout.SOUTH);

        // --- SỰ KIỆN NÚT BẤM ---
        btnOpen.addActionListener(e -> handleOpenSaving());
        btnSettle.addActionListener(e -> handleSettleSaving());
        btnRefresh.addActionListener(e -> loadSavingsData());
    }

    private void handleOpenSaving() {
        try {
            String text = txtDepositAmount.getText().trim();
            if (text.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập số tiền gửi!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            double amount = Double.parseDouble(text);
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this, "Số tiền gửi phải lớn hơn 0!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            TermOption selected = (TermOption) cbTerms.getSelectedItem();
            if (selected == null) return;

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn có chắc muốn gửi tiết kiệm " + currencyFormat.format(amount) + "\n" +
                            "Kỳ hạn: " + selected.getSeconds() + " giây | Lãi suất: " + selected.getRate() + "%/kỳ?",
                    "Xác Nhận Mở Sổ", JOptionPane.YES_NO_OPTION);

            if (confirm != JOptionPane.YES_OPTION) return;

            boolean ok = bankService.openSaving(currentAccount.getAccountNumber(), amount, selected.getRate(), selected.getSeconds());
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Chúc mừng! Bạn đã mở sổ tiết kiệm thành công.\nTiền lãi sẽ được hệ thống ngầm tự động cộng theo chu kỳ!",
                        "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                txtDepositAmount.setText("");
                loadSavingsData();
                if (onBalanceChangedCallback != null) {
                    onBalanceChangedCallback.run();
                }
            } else {
                JOptionPane.showMessageDialog(this,
                        "Mở sổ thất bại: Số dư tài khoản không đủ hoặc tài khoản bị hạn chế!",
                        "Thất Bại", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số tiền không hợp lệ. Vui lòng chỉ nhập số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối Server RMI: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void handleSettleSaving() {
        int selectedRow = tableSavings.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 sổ tiết kiệm trong bảng để tất toán!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int savingId = (int) tableModel.getValueAt(selectedRow, 0);
        String status = (String) tableModel.getValueAt(selectedRow, 6);
        String totalReturnStr = (String) tableModel.getValueAt(selectedRow, 5);

        if ("CLOSED".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Sổ này đã được tất toán trước đó!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn tất toán Sổ #" + savingId + "?\n" +
                        "Toàn bộ Tiền gốc + Lãi (" + totalReturnStr + ") sẽ được chuyển về tài khoản chính ngay lập tức.",
                "Xác Nhận Tất Toán", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            boolean ok = bankService.settleSaving(savingId);
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Tất toán sổ #" + savingId + " thành công!\nToàn bộ tiền đã được chuyển về tài khoản thanh toán.",
                        "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                loadSavingsData();
                if (onBalanceChangedCallback != null) {
                    onBalanceChangedCallback.run();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Tất toán thất bại hoặc sổ đã bị đóng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối RMI: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /**
     * Tải lại danh sách sổ tiết kiệm từ Server
     */
    public void loadSavingsData() {
        SwingUtilities.invokeLater(() -> {
            try {
                tableModel.setRowCount(0);
                List<Saving> list = bankService.getSavingsByAccount(currentAccount.getAccountNumber());
                double totalBalance = 0.0;

                for (Saving s : list) {
                    double total = s.getDepositAmount() + s.getAccumulatedInterest();
                    if ("ACTIVE".equalsIgnoreCase(s.getStatus())) {
                        totalBalance += total;
                    }

                    tableModel.addRow(new Object[]{
                            s.getId(),
                            currencyFormat.format(s.getDepositAmount()),
                            s.getInterestRate() + "%",
                            s.getTermPeriod() + "s",
                            currencyFormat.format(s.getAccumulatedInterest()),
                            currencyFormat.format(total),
                            s.getStatus(),
                            s.getCreatedAt() != null ? dateFormat.format(s.getCreatedAt()) : ""
                    });
                }
                lblTotalSavingBalance.setText("Tổng tiền trong sổ ACTIVE: " + currencyFormat.format(totalBalance));
            } catch (RemoteException e) {
                System.err.println("Không thể tải danh sách sổ tiết kiệm: " + e.getMessage());
            }
        });
    }
}
