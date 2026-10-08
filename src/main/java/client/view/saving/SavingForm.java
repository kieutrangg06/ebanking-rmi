package client.view.saving;

import client.view.ui.ModernButton;
import client.view.ui.ModernTextField;
import client.view.ui.RoundedPanel;
import client.view.ui.UITheme;
import common.models.Account;
import common.models.Saving;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.rmi.RemoteException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Giao diện Quản lý Tiết Kiệm Tích Lũy Trực Tuyến.
 * Đồng bộ chuẩn thiết kế Neo-Banking hiện đại (UITheme), tích hợp đa luồng tính lãi tự động theo chu kỳ
 * và tất toán bảo toàn vốn lẫn lãi.
 */
public class SavingForm extends JPanel {

    private final IBankService bankService;
    private final Account currentAccount;
    private final Runnable onBalanceChangedCallback;

    private JTable tableSavings;
    private DefaultTableModel tableModel;
    private ModernTextField txtDepositAmount;
    private JComboBox<TermOption> cbTerms;
    private JLabel lblTotalSavingBalance;
    private JLabel lblTotalInterestAccumulated;
    private JLabel lblEstimatePreview;
    private ModernButton btnSettle;

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

        setLayout(new BorderLayout(14, 14));
        setBorder(new EmptyBorder(16, 20, 16, 20));
        setBackground(UITheme.BG_MAIN);

        initUI();
        loadSavingsData();
    }

    private void initUI() {
        // =====================================================================
        // PHẦN 1: PANEL TRÊN - TỔNG QUAN KPI & MỞ SỔ TIẾT KIỆM MỚI
        // =====================================================================
        RoundedPanel topCard = new RoundedPanel(16, Color.WHITE);
        topCard.setLayout(new BorderLayout(14, 14));
        topCard.setBorder(new EmptyBorder(18, 22, 18, 22));
        topCard.setShowShadow(true);

        // Header Tiêu đề
        JPanel headerBox = new JPanel(new BorderLayout(5, 5));
        headerBox.setOpaque(false);

        JLabel lblTitle = new JLabel("💰 TIẾT KIỆM TÍCH LŨY THÔNG MINH");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);

        JLabel lblSub = new JLabel("Lãi suất sinh lời lũy kế theo chu kỳ với tính an toàn và minh bạch tuyệt đối.");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_MUTED);

        headerBox.add(lblTitle, BorderLayout.NORTH);
        headerBox.add(lblSub, BorderLayout.SOUTH);
        topCard.add(headerBox, BorderLayout.NORTH);

        // Hàng Thẻ KPI tóm tắt số dư tiết kiệm
        JPanel kpiRow = new JPanel(new GridLayout(1, 2, 16, 0));
        kpiRow.setOpaque(false);
        kpiRow.setPreferredSize(new Dimension(0, 68));

        // KPI Card 1: Tổng gốc đang gửi (ACTIVE)
        RoundedPanel card1 = new RoundedPanel(12, UITheme.PRIMARY_LIGHT);
        card1.setLayout(new BorderLayout(4, 4));
        card1.setBorder(new EmptyBorder(8, 14, 8, 14));
        JLabel lblKpi1Title = new JLabel("TỔNG TIỀN GỬI TIẾT KIỆM (SỔ ACTIVE)");
        lblKpi1Title.setFont(UITheme.FONT_SMALL_BOLD);
        lblKpi1Title.setForeground(UITheme.PRIMARY_DARK);
        lblTotalSavingBalance = new JLabel("0 VNĐ");
        lblTotalSavingBalance.setFont(UITheme.FONT_TITLE_XL);
        lblTotalSavingBalance.setForeground(UITheme.PRIMARY);
        card1.add(lblKpi1Title, BorderLayout.NORTH);
        card1.add(lblTotalSavingBalance, BorderLayout.CENTER);

        // KPI Card 2: Tổng lãi tích lũy đã sinh
        RoundedPanel card2 = new RoundedPanel(12, UITheme.SUCCESS_LIGHT);
        card2.setLayout(new BorderLayout(4, 4));
        card2.setBorder(new EmptyBorder(8, 14, 8, 14));
        JLabel lblKpi2Title = new JLabel("TỔNG LÃI TÍCH LŨY ĐÃ SINH RA");
        lblKpi2Title.setFont(UITheme.FONT_SMALL_BOLD);
        lblKpi2Title.setForeground(UITheme.SUCCESS_DARK);
        lblTotalInterestAccumulated = new JLabel("+0 VNĐ");
        lblTotalInterestAccumulated.setFont(UITheme.FONT_TITLE_XL);
        lblTotalInterestAccumulated.setForeground(UITheme.SUCCESS_DARK);
        card2.add(lblKpi2Title, BorderLayout.NORTH);
        card2.add(lblTotalInterestAccumulated, BorderLayout.CENTER);

        kpiRow.add(card1);
        kpiRow.add(card2);
        topCard.add(kpiRow, BorderLayout.CENTER);

        // Khung Nhập Liệu Mở Sổ & Dự Tính Lãi Suất Trực Tiếp
        JPanel inputWrapper = new JPanel(new BorderLayout(8, 8));
        inputWrapper.setOpaque(false);

        JPanel formGrid = new JPanel(new GridBagLayout());
        formGrid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Cột 1: Số tiền gửi
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.35;
        JLabel lblAmt = new JLabel("Số tiền gửi (VNĐ):");
        lblAmt.setFont(UITheme.FONT_BODY_BOLD);
        lblAmt.setForeground(UITheme.TEXT_MAIN);
        formGrid.add(lblAmt, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        txtDepositAmount = new ModernTextField("Nhập số tiền gửi (ví dụ: 1000000)...");
        txtDepositAmount.setText("1000000");
        txtDepositAmount.setPreferredSize(new Dimension(200, 38));
        formGrid.add(txtDepositAmount, gbc);

        // Cột 2: Gói kỳ hạn
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.45;
        JLabel lblTerm = new JLabel("Gói kỳ hạn & Lãi suất sinh lời:");
        lblTerm.setFont(UITheme.FONT_BODY_BOLD);
        lblTerm.setForeground(UITheme.TEXT_MAIN);
        formGrid.add(lblTerm, gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        cbTerms = new JComboBox<>(new TermOption[]{
                new TermOption("⚡ Siêu ngắn: 15 giây (Lãi 5.0% / chu kỳ)", 15, 5.0),
                new TermOption("🌱 Linh hoạt: 30 giây (Lãi 7.0% / chu kỳ)", 30, 7.0),
                new TermOption("🏆 Tối ưu: 60 giây (Lãi 10.0% / chu kỳ)", 60, 10.0)
        });
        cbTerms.setFont(UITheme.FONT_BODY);
        cbTerms.setBackground(Color.WHITE);
        cbTerms.setPreferredSize(new Dimension(240, 38));
        formGrid.add(cbTerms, gbc);

        // Cột 3: Nút Mở Sổ
        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0.20;
        ModernButton btnOpen = new ModernButton("✨ Mở Sổ Tiết Kiệm", ModernButton.Style.SUCCESS);
        btnOpen.setPreferredSize(new Dimension(170, 38));
        formGrid.add(btnOpen, gbc);

        inputWrapper.add(formGrid, BorderLayout.NORTH);

        // Dự tính lãi suất thời gian thực
        lblEstimatePreview = new JLabel("💡 Dự kiến: Nhận +50,000 VNĐ lãi sau mỗi chu kỳ 15s");
        lblEstimatePreview.setFont(UITheme.FONT_SMALL_BOLD);
        lblEstimatePreview.setForeground(UITheme.PRIMARY);
        lblEstimatePreview.setBorder(new EmptyBorder(2, 8, 2, 8));
        inputWrapper.add(lblEstimatePreview, BorderLayout.SOUTH);

        topCard.add(inputWrapper, BorderLayout.SOUTH);
        add(topCard, BorderLayout.NORTH);

        // =====================================================================
        // PHẦN 2: BẢNG DANH SÁCH CÁC SỔ TIẾT KIỆM (CARD GIỮA)
        // =====================================================================
        RoundedPanel centerCard = new RoundedPanel(16, Color.WHITE);
        centerCard.setLayout(new BorderLayout(10, 10));
        centerCard.setBorder(new EmptyBorder(16, 20, 16, 20));
        centerCard.setShowShadow(true);

        JPanel tableTitleBar = new JPanel(new BorderLayout());
        tableTitleBar.setOpaque(false);

        JLabel lblListTitle = new JLabel("DANH SÁCH SỔ TIẾT KIỆM ĐANG QUẢN LÝ");
        lblListTitle.setFont(UITheme.FONT_SUBTITLE);
        lblListTitle.setForeground(UITheme.PRIMARY_DARK);

        JLabel lblListSub = new JLabel("Hệ thống đa luồng (Multi-threading) tự động cộng dồn tiền lãi theo từng chu kỳ.");
        lblListSub.setFont(UITheme.FONT_SMALL);
        lblListSub.setForeground(UITheme.TEXT_MUTED);

        JPanel tableTitleGroup = new JPanel(new BorderLayout(3, 3));
        tableTitleGroup.setOpaque(false);
        tableTitleGroup.add(lblListTitle, BorderLayout.NORTH);
        tableTitleGroup.add(lblListSub, BorderLayout.SOUTH);
        tableTitleBar.add(tableTitleGroup, BorderLayout.WEST);

        centerCard.add(tableTitleBar, BorderLayout.NORTH);

        String[] columns = {"Mã Sổ", "Số Tiền Gốc", "Lãi Suất", "Chu Kỳ", "Lãi Tích Lũy", "Tổng Nhận Được", "Trạng Thái", "Ngày Mở"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tableSavings = new JTable(tableModel);
        tableSavings.setRowHeight(32);
        tableSavings.setFont(UITheme.FONT_BODY);
        tableSavings.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableSavings.setGridColor(UITheme.BORDER_COLOR);
        tableSavings.setShowVerticalLines(false);

        // Header bảng
        tableSavings.getTableHeader().setFont(UITheme.FONT_BODY_BOLD);
        tableSavings.getTableHeader().setBackground(UITheme.PRIMARY_LIGHT);
        tableSavings.getTableHeader().setForeground(UITheme.PRIMARY_DARK);
        tableSavings.getTableHeader().setPreferredSize(new Dimension(0, 36));

        // Render canh lề & Format màu sắc
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);

        tableSavings.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tableSavings.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);
        tableSavings.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        tableSavings.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        // Render cột Tiền lãi (Xanh lục đậm)
        tableSavings.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(JLabel.RIGHT);
                setForeground(UITheme.SUCCESS_DARK);
                setFont(UITheme.FONT_BODY_BOLD);
                return c;
            }
        });

        // Render cột Tổng nhận (Màu Primary)
        tableSavings.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(JLabel.RIGHT);
                setForeground(UITheme.PRIMARY);
                setFont(UITheme.FONT_BODY_BOLD);
                return c;
            }
        });

        // Render Cột Trạng thái (Pill Badge)
        tableSavings.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                lbl.setHorizontalAlignment(JLabel.CENTER);
                lbl.setFont(UITheme.FONT_SMALL_BOLD);
                String st = val != null ? val.toString() : "";
                if ("ACTIVE".equalsIgnoreCase(st)) {
                    lbl.setText("🟢 ACTIVE");
                    lbl.setForeground(UITheme.SUCCESS_DARK);
                } else {
                    lbl.setText("⚪ CLOSED");
                    lbl.setForeground(UITheme.TEXT_MUTED);
                }
                return lbl;
            }
        });

        tableSavings.getColumnModel().getColumn(7).setCellRenderer(centerRenderer);

        JScrollPane scrollPane = new JScrollPane(tableSavings);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 1));
        centerCard.add(scrollPane, BorderLayout.CENTER);

        // =====================================================================
        // PHẦN 3: THANH THAO TÁC DƯỚI CÙNG (TẤT TOÁN, LÀM MỚI)
        // =====================================================================
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
        bottomBar.setOpaque(false);

        ModernButton btnRefresh = new ModernButton("🔄 Làm Mới Danh Sách", ModernButton.Style.SECONDARY);
        btnRefresh.setPreferredSize(new Dimension(170, 36));

        btnSettle = new ModernButton("💎 Tất Toán Sổ (Rút Gốc + Lãi)", ModernButton.Style.PRIMARY);
        btnSettle.setPreferredSize(new Dimension(240, 36));

        bottomBar.add(btnRefresh);
        bottomBar.add(btnSettle);
        centerCard.add(bottomBar, BorderLayout.SOUTH);

        add(centerCard, BorderLayout.CENTER);

        // =====================================================================
        // SỰ KIỆN GIAO DIỆN
        // =====================================================================
        btnOpen.addActionListener(e -> handleOpenSaving());
        btnSettle.addActionListener(e -> handleSettleSaving());
        btnRefresh.addActionListener(e -> loadSavingsData());

        // Cập nhật tính nhẩm lãi suất khi người dùng gõ tiền hoặc đổi kỳ hạn
        txtDepositAmount.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateEstimate(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateEstimate(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateEstimate(); }
        });
        cbTerms.addActionListener(e -> updateEstimate());

        updateEstimate();
    }

    private void updateEstimate() {
        try {
            String text = txtDepositAmount.getText().trim();
            if (text.isEmpty()) {
                lblEstimatePreview.setText("💡 Hãy nhập số tiền gửi để xem dự tính tiền lãi...");
                return;
            }
            double amount = Double.parseDouble(text);
            TermOption opt = (TermOption) cbTerms.getSelectedItem();
            if (opt != null && amount > 0) {
                double interest = amount * (opt.getRate() / 100.0);
                double total = amount + interest;
                lblEstimatePreview.setText(String.format("💡 Dự kiến: Nhận +%s lãi sau mỗi chu kỳ %ds (Tổng gốc + lãi: %s)",
                        currencyFormat.format(interest), opt.getSeconds(), currencyFormat.format(total)));
            }
        } catch (NumberFormatException ignored) {
            lblEstimatePreview.setText("⚠️ Số tiền không hợp lệ!");
        }
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
                    "Bạn có chắc muốn mở sổ tiết kiệm với thông tin sau?\n\n" +
                            "• Số tiền gửi: " + currencyFormat.format(amount) + "\n" +
                            "• Kỳ hạn tính lãi: " + selected.getSeconds() + " giây / chu kỳ\n" +
                            "• Lãi suất cam kết: " + selected.getRate() + "% / chu kỳ\n\n" +
                            "Hệ thống sẽ tự động trừ số dư tài khoản thanh toán và bắt đầu tính lãi.",
                    "Xác Nhận Mở Sổ Tiết Kiệm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) return;

            boolean ok = bankService.openSaving(currentAccount.getAccountNumber(), amount, selected.getRate(), selected.getSeconds());
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Chúc mừng! Bạn đã mở sổ tiết kiệm thành công.\nTiền lãi sẽ được tiến trình nền tự động tích lũy theo từng chu kỳ!",
                        "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                txtDepositAmount.setText("1000000");
                loadSavingsData();
                if (onBalanceChangedCallback != null) {
                    onBalanceChangedCallback.run();
                }
            } else {
                JOptionPane.showMessageDialog(this,
                        "Mở sổ thất bại: Số dư tài khoản thanh toán không đủ hoặc tài khoản bị giới hạn!",
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
        String principalStr = (String) tableModel.getValueAt(selectedRow, 1);
        String interestStr = (String) tableModel.getValueAt(selectedRow, 4);
        String totalReturnStr = (String) tableModel.getValueAt(selectedRow, 5);

        if ("CLOSED".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Sổ #" + savingId + " đã được tất toán trước đó!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "XÁC NHẬN TẤT TOÁN SỔ TIẾT KIỆM #" + savingId + "\n\n" +
                        "• Tiền gốc: " + principalStr + "\n" +
                        "• Lãi tích lũy đã sinh: " + interestStr + "\n" +
                        "• TỔNG TIỀN HOÀN VỀ VÍ CHÍNH: " + totalReturnStr + "\n\n" +
                        "Bạn có chắc chắn muốn kết thúc sổ và rút toàn bộ tiền về tài khoản thanh toán?",
                "Xác Nhận Tất Toán", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            boolean ok = bankService.settleSaving(savingId);
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Tất toán sổ tiết kiệm #" + savingId + " thành công!\n" +
                                "Toàn bộ tiền gốc và lãi (" + totalReturnStr + ") đã được chuyển về tài khoản thanh toán.",
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
     * Tải lại danh sách sổ tiết kiệm từ Server và cập nhật các thẻ số liệu thống kê
     */
    public void loadSavingsData() {
        SwingUtilities.invokeLater(() -> {
            try {
                tableModel.setRowCount(0);
                List<Saving> list = bankService.getSavingsByAccount(currentAccount.getAccountNumber());
                double totalActiveDeposit = 0.0;
                double totalActiveInterest = 0.0;

                for (Saving s : list) {
                    double total = s.getDepositAmount() + s.getAccumulatedInterest();
                    if ("ACTIVE".equalsIgnoreCase(s.getStatus())) {
                        totalActiveDeposit += s.getDepositAmount();
                        totalActiveInterest += s.getAccumulatedInterest();
                    }

                    tableModel.addRow(new Object[]{
                            s.getId(),
                            currencyFormat.format(s.getDepositAmount()),
                            s.getInterestRate() + "%",
                            s.getTermPeriod() + "s",
                            "+" + currencyFormat.format(s.getAccumulatedInterest()),
                            currencyFormat.format(total),
                            s.getStatus(),
                            s.getCreatedAt() != null ? dateFormat.format(s.getCreatedAt()) : ""
                    });
                }

                lblTotalSavingBalance.setText(currencyFormat.format(totalActiveDeposit));
                lblTotalInterestAccumulated.setText("+" + currencyFormat.format(totalActiveInterest));
            } catch (RemoteException e) {
                System.err.println("Không thể tải danh sách sổ tiết kiệm: " + e.getMessage());
            }
        });
    }
}
