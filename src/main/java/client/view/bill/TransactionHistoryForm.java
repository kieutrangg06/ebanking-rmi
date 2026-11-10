package client.view.bill;

import client.view.ui.ModernButton;
import client.view.ui.ModernTextField;
import client.view.ui.RoundedPanel;
import client.view.ui.UITheme;
import common.models.Account;
import common.models.Transaction;
import common.rmi.IBankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Giao diện Xem Lịch Sử Giao Dịch & Xuất Sao Kê Tài Khoản - Tích hợp đồng bộ trong DashboardForm.
 * Hỗ trợ bộ lọc thời gian thực, hiển thị biến động số dư trực quan (+ / -) và xuất file CSV chuẩn Excel.
 */
public class TransactionHistoryForm extends JPanel {

    private final IBankService bankService;
    private final Account account;

    private ModernTextField txtFromDate;
    private ModernTextField txtToDate;
    private ModernButton btnFilter;
    private ModernButton btnAll;
    private ModernButton btnToday;
    private ModernButton btn7Days;
    private ModernButton btnExportCsv;

    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblTotalCount;
    private JLabel lblTotalIn;
    private JLabel lblTotalOut;
    private JLabel lblStatus;

    private final List<Transaction> currentTransactions = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public TransactionHistoryForm(IBankService bankService, Account account) {
        this.bankService = bankService;
        this.account = account;

        dateFormat.setLenient(false);
        initUI();
        loadTransactions(null, null);
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 14));
        setBackground(UITheme.BG_MAIN);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. TOP CONTAINER: HEADER, STATS, & FILTER
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(false);

        // Header Title Card
        RoundedPanel headerCard = new RoundedPanel(16, Color.WHITE);
        headerCard.setLayout(new BorderLayout(14, 12));
        headerCard.setBorder(new EmptyBorder(16, 20, 16, 20));
        headerCard.setShowShadow(true);

        JPanel titleBox = new JPanel(new BorderLayout(4, 4));
        titleBox.setOpaque(false);

        JLabel lblTitle = new JLabel("📜 NHẬT KÝ BIẾN ĐỘNG SỐ DƯ & SAO KÊ TÀI CHÍNH");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);

        JLabel lblSub = new JLabel("Ghi nhận minh bạch toàn bộ giao dịch chuyển khoản, nhận tiền, nạp/rút tiền, thanh toán cước và tiền lãi tích lũy qua RMI.");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_MUTED);

        titleBox.add(lblTitle, BorderLayout.NORTH);
        titleBox.add(lblSub, BorderLayout.SOUTH);
        headerCard.add(titleBox, BorderLayout.NORTH);

        // Stats Row (KPI)
        JPanel statsRow = new JPanel(new GridLayout(1, 3, 14, 0));
        statsRow.setOpaque(false);
        statsRow.setPreferredSize(new Dimension(0, 68));

        lblTotalCount = new JLabel("0 giao dịch", SwingConstants.LEFT);
        JPanel cardCount = createMiniKpiCard("TỔNG GIAO DỊCH", lblTotalCount, UITheme.PRIMARY);

        lblTotalIn = new JLabel("+0 VNĐ", SwingConstants.LEFT);
        JPanel cardIn = createMiniKpiCard("TỔNG TIỀN VÀO (+)", lblTotalIn, UITheme.SUCCESS_DARK);

        lblTotalOut = new JLabel("-0 VNĐ", SwingConstants.LEFT);
        JPanel cardOut = createMiniKpiCard("TỔNG TIỀN RA (-)", lblTotalOut, UITheme.DANGER);

        statsRow.add(cardCount);
        statsRow.add(cardIn);
        statsRow.add(cardOut);
        headerCard.add(statsRow, BorderLayout.CENTER);

        topContainer.add(headerCard);
        topContainer.add(Box.createVerticalStrut(12));

        // Filter Bar Card
        RoundedPanel filterCard = new RoundedPanel(14, Color.WHITE);
        filterCard.setLayout(new BorderLayout(12, 10));
        filterCard.setBorder(new EmptyBorder(12, 16, 12, 16));
        filterCard.setShowShadow(true);

        JPanel filterInputs = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterInputs.setOpaque(false);

        JLabel lblFrom = new JLabel("Từ ngày:");
        lblFrom.setFont(UITheme.FONT_BODY_BOLD);
        txtFromDate = new ModernTextField("yyyy-MM-dd");
        txtFromDate.setPreferredSize(new Dimension(110, 34));

        JLabel lblTo = new JLabel("Đến ngày:");
        lblTo.setFont(UITheme.FONT_BODY_BOLD);
        txtToDate = new ModernTextField("yyyy-MM-dd");
        txtToDate.setPreferredSize(new Dimension(110, 34));

        btnFilter = new ModernButton("🔍 Lọc", ModernButton.Style.PRIMARY);
        btnFilter.setFont(UITheme.FONT_SMALL_BOLD);
        btnFilter.setPreferredSize(new Dimension(75, 34));
        btnFilter.addActionListener(e -> performFilter());

        // Quick date shortcuts
        btnAll = new ModernButton("Tất cả", ModernButton.Style.SECONDARY);
        btnAll.setFont(UITheme.FONT_SMALL);
        btnAll.setPreferredSize(new Dimension(75, 34));
        btnAll.addActionListener(e -> {
            txtFromDate.setText("");
            txtToDate.setText("");
            loadTransactions(null, null);
        });

        btnToday = new ModernButton("Hôm nay", ModernButton.Style.SECONDARY);
        btnToday.setFont(UITheme.FONT_SMALL);
        btnToday.setPreferredSize(new Dimension(80, 34));
        btnToday.addActionListener(e -> {
            String todayStr = dateFormat.format(new Date());
            txtFromDate.setText(todayStr);
            txtToDate.setText(todayStr);
            performFilter();
        });

        btn7Days = new ModernButton("7 ngày qua", ModernButton.Style.SECONDARY);
        btn7Days.setFont(UITheme.FONT_SMALL);
        btn7Days.setPreferredSize(new Dimension(90, 34));
        btn7Days.addActionListener(e -> {
            Calendar cal = Calendar.getInstance();
            Date end = cal.getTime();
            cal.add(Calendar.DAY_OF_YEAR, -7);
            Date start = cal.getTime();
            txtFromDate.setText(dateFormat.format(start));
            txtToDate.setText(dateFormat.format(end));
            performFilter();
        });

        filterInputs.add(lblFrom);
        filterInputs.add(txtFromDate);
        filterInputs.add(lblTo);
        filterInputs.add(txtToDate);
        filterInputs.add(btnFilter);
        filterInputs.add(btnAll);
        filterInputs.add(btnToday);
        filterInputs.add(btn7Days);

        filterCard.add(filterInputs, BorderLayout.WEST);

        // Export CSV Action
        JPanel exportBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        exportBox.setOpaque(false);
        btnExportCsv = new ModernButton("📥 Xuất File CSV", ModernButton.Style.SUCCESS);
        btnExportCsv.setFont(UITheme.FONT_BODY_BOLD);
        btnExportCsv.setPreferredSize(new Dimension(145, 34));
        btnExportCsv.addActionListener(e -> performExportCsv());
        exportBox.add(btnExportCsv);

        filterCard.add(exportBox, BorderLayout.EAST);
        topContainer.add(filterCard);

        add(topContainer, BorderLayout.NORTH);

        // 2. TABLE CONTAINER: MODERN TABLE OF TRANSACTIONS
        RoundedPanel tableCard = new RoundedPanel(16, Color.WHITE);
        tableCard.setLayout(new BorderLayout(0, 8));
        tableCard.setBorder(new EmptyBorder(14, 14, 14, 14));
        tableCard.setShowShadow(true);

        String[] columnNames = {
                "STT", "Mã GD", "Loại Giao Dịch", "Tài Khoản Gửi", "Tài Khoản Nhận",
                "Số Tiền (VNĐ)", "Nội Dung", "Thời Gian", "Trạng Thái"
        };

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        UITheme.applyModernTableStyle(table);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(45);
        table.getColumnModel().getColumn(1).setPreferredWidth(65);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setPreferredWidth(140);
        table.getColumnModel().getColumn(6).setPreferredWidth(220);
        table.getColumnModel().getColumn(7).setPreferredWidth(140);
        table.getColumnModel().getColumn(8).setPreferredWidth(95);

        // Custom Cell Renderers: Color code +/- amounts & status badges
        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                String valStr = value != null ? value.toString() : "";
                setHorizontalAlignment(SwingConstants.RIGHT);
                setFont(UITheme.FONT_BODY_BOLD);

                if (!isSelected) {
                    if (valStr.startsWith("+")) {
                        c.setForeground(UITheme.SUCCESS_DARK);
                        c.setBackground(row % 2 == 0 ? new Color(240, 253, 244) : new Color(246, 254, 249));
                    } else if (valStr.startsWith("-")) {
                        c.setForeground(UITheme.DANGER);
                        c.setBackground(row % 2 == 0 ? new Color(254, 242, 242) : new Color(255, 247, 247));
                    } else {
                        c.setForeground(UITheme.TEXT_MAIN);
                        c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 251, 253));
                    }
                }
                return c;
            }
        });

        table.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UITheme.FONT_SMALL_BOLD);
                if (!isSelected) {
                    c.setForeground(UITheme.SUCCESS_DARK);
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR));
        scrollPane.getViewport().setBackground(Color.WHITE);

        tableCard.add(scrollPane, BorderLayout.CENTER);

        // Status bar under table
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setOpaque(false);
        statusBar.setBorder(new EmptyBorder(6, 4, 2, 4));

        lblStatus = new JLabel("Dữ liệu sẵn sàng.", SwingConstants.LEFT);
        lblStatus.setFont(UITheme.FONT_SMALL);
        lblStatus.setForeground(UITheme.TEXT_MUTED);
        statusBar.add(lblStatus, BorderLayout.WEST);

        tableCard.add(statusBar, BorderLayout.SOUTH);
        add(tableCard, BorderLayout.CENTER);
    }

    private JPanel createMiniKpiCard(String title, JLabel valueLabel, Color accentColor) {
        RoundedPanel card = new RoundedPanel(12, UITheme.BG_INPUT);
        card.setLayout(new BorderLayout(4, 4));
        card.setBorder(new EmptyBorder(8, 14, 8, 14));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UITheme.FONT_SMALL_BOLD);
        lblTitle.setForeground(UITheme.TEXT_MUTED);

        valueLabel.setFont(UITheme.FONT_TITLE);
        valueLabel.setForeground(accentColor);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    public void refreshData() {
        performFilter();
    }

    private void performFilter() {
        String fromStr = txtFromDate.getText().trim();
        String toStr = txtToDate.getText().trim();

        if (fromStr.isEmpty() && toStr.isEmpty()) {
            loadTransactions(null, null);
            return;
        }

        Date fromDate = null;
        Date toDate = null;
        String dateRegex = "^\\d{4}-\\d{2}-\\d{2}$";

        if (!fromStr.isEmpty()) {
            if (!fromStr.matches(dateRegex)) {
                JOptionPane.showMessageDialog(this, "Ngày bắt đầu sai định dạng! Vui lòng nhập đúng 'yyyy-MM-dd' (VD: 2026-09-01).", "Lỗi Định Dạng", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                fromDate = dateFormat.parse(fromStr);
            } catch (ParseException ex) {
                JOptionPane.showMessageDialog(this, "Ngày bắt đầu không hợp lệ (không tồn tại trong lịch)!", "Lỗi Ngày Tháng", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        if (!toStr.isEmpty()) {
            if (!toStr.matches(dateRegex)) {
                JOptionPane.showMessageDialog(this, "Ngày kết thúc sai định dạng! Vui lòng nhập đúng 'yyyy-MM-dd' (VD: 2026-09-30).", "Lỗi Định Dạng", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                toDate = dateFormat.parse(toStr);
            } catch (ParseException ex) {
                JOptionPane.showMessageDialog(this, "Ngày kết thúc không hợp lệ (không tồn tại trong lịch)!", "Lỗi Ngày Tháng", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        if (fromDate != null && toDate != null && fromDate.after(toDate)) {
            JOptionPane.showMessageDialog(this, "Ngày bắt đầu không được lớn hơn ngày kết thúc!", "Lỗi Khoảng Thời Gian", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loadTransactions(fromDate, toDate);
    }

    public void loadTransactions(Date fromDate, Date toDate) {
        btnFilter.setEnabled(false);
        lblStatus.setText("Đang tải dữ liệu từ Server...");
        lblStatus.setForeground(UITheme.PRIMARY);

        SwingWorker<List<Transaction>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Transaction> doInBackground() throws Exception {
                if (fromDate == null && toDate == null) {
                    return bankService.getTransactionHistory(account.getAccountNumber());
                } else {
                    return bankService.getTransactionHistoryFiltered(account.getAccountNumber(), fromDate, toDate);
                }
            }

            @Override
            protected void done() {
                btnFilter.setEnabled(true);
                try {
                    List<Transaction> list = get();
                    currentTransactions.clear();
                    if (list != null) {
                        currentTransactions.addAll(list);
                    }
                    displayTransactions(currentTransactions);
                    lblStatus.setText("Đã tải xong " + currentTransactions.size() + " bản ghi lúc " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
                    lblStatus.setForeground(UITheme.TEXT_MUTED);
                } catch (InterruptedException | ExecutionException ex) {
                    lblStatus.setText("Lỗi kết nối Server: " + ex.getMessage());
                    lblStatus.setForeground(UITheme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void displayTransactions(List<Transaction> list) {
        tableModel.setRowCount(0);
        double totalIn = 0;
        double totalOut = 0;
        int stt = 1;

        String myAcc = account.getAccountNumber();

        for (Transaction tx : list) {
            boolean isSender = myAcc.equalsIgnoreCase(tx.getFromAccount());
            boolean isReceiver = myAcc.equalsIgnoreCase(tx.getToAccount());

            String amountStr;
            if (isReceiver && !isSender) {
                amountStr = "+" + UITheme.MONEY_FORMAT.format(tx.getAmount());
                totalIn += tx.getAmount();
            } else if (isSender && !isReceiver) {
                amountStr = "-" + UITheme.MONEY_FORMAT.format(tx.getAmount());
                totalOut += tx.getAmount();
            } else {
                amountStr = UITheme.MONEY_FORMAT.format(tx.getAmount());
            }

            String timeStr = tx.getCreatedAt() != null ? timestampFormat.format(tx.getCreatedAt()) : "";
            String typeDisplay = formatTransactionType(tx.getTransactionType());

            tableModel.addRow(new Object[]{
                    stt++,
                    "#" + tx.getId(),
                    typeDisplay,
                    tx.getFromAccount() != null ? tx.getFromAccount() : "---",
                    tx.getToAccount() != null ? tx.getToAccount() : "---",
                    amountStr,
                    tx.getDescription(),
                    timeStr,
                    "THÀNH CÔNG"
            });
        }

        lblTotalCount.setText(list.size() + " giao dịch");
        lblTotalIn.setText("+" + UITheme.MONEY_FORMAT.format(totalIn));
        lblTotalOut.setText("-" + UITheme.MONEY_FORMAT.format(totalOut));
    }

    private String formatTransactionType(String type) {
        if (type == null) return "KHÁC";
        switch (type.toUpperCase()) {
            case "CHUYEN_TIEN":
                return "💸 Chuyển Khoản";
            case "THANH_TOAN_HOA_DON":
                return "🧾 Thanh Toán Cước";
            case "MO_SO_TIET_KIEM":
                return "💰 Mở Tiết Kiệm";
            case "TAT_TOAN_TIET_KIEM":
                return "📥 Tất Toán Tiết Kiệm";
            case "NAP_TIEN":
                return "💳 Nạp Tiền";
            case "RUT_TIEN":
                return "🏧 Rút Tiền";
            default:
                return type;
        }
    }

    private void performExportCsv() {
        if (currentTransactions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Không có dữ liệu giao dịch nào để xuất sao kê!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Lưu Báo Cáo Sao Kê (CSV)");
        fileChooser.setSelectedFile(new File("SaoKe_" + account.getAccountNumber() + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File fileToSave = fileChooser.getSelectedFile();
        if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
            fileToSave = new File(fileToSave.getAbsolutePath() + ".csv");
        }

        try (FileOutputStream fos = new FileOutputStream(fileToSave);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

            // Ghi UTF-8 BOM để Excel hiển thị đúng dấu tiếng Việt
            fos.write(0xEF);
            fos.write(0xBB);
            fos.write(0xBF);

            osw.write("BÁO CÁO SAO KÊ GIAO DỊCH TÀI KHOẢN NGÂN HÀNG\n");
            osw.write("Chủ tài khoản," + escapeCsv(account.getFullName()) + "\n");
            osw.write("Số tài khoản," + escapeCsv(account.getAccountNumber()) + "\n");
            osw.write("Thời điểm xuất file," + escapeCsv(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date())) + "\n\n");

            osw.write("STT,Mã GD,Loại Giao Dịch,Tài Khoản Gửi,Tài Khoản Nhận,Dấu,Số Tiền (VNĐ),Nội Dung,Thời Gian,Trạng Thái\n");

            int stt = 1;
            String myAcc = account.getAccountNumber();

            for (Transaction tx : currentTransactions) {
                boolean isSender = myAcc.equalsIgnoreCase(tx.getFromAccount());
                boolean isReceiver = myAcc.equalsIgnoreCase(tx.getToAccount());
                String amtPrefix = (isReceiver && !isSender) ? "+" : ((isSender && !isReceiver) ? "-" : "");

                String row = String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        stt++,
                        escapeCsv(String.valueOf(tx.getId())),
                        escapeCsv(formatTransactionType(tx.getTransactionType())),
                        escapeCsv(tx.getFromAccount() != null ? tx.getFromAccount() : ""),
                        escapeCsv(tx.getToAccount() != null ? tx.getToAccount() : ""),
                        amtPrefix,
                        String.format("%.0f", tx.getAmount()),
                        escapeCsv(tx.getDescription() != null ? tx.getDescription() : ""),
                        escapeCsv(tx.getCreatedAt() != null ? timestampFormat.format(tx.getCreatedAt()) : ""),
                        "THANH_CONG"
                );
                osw.write(row);
            }

            osw.flush();
            JOptionPane.showMessageDialog(this,
                    "Xuất sao kê giao dịch thành công!\nĐường dẫn: " + fileToSave.getAbsolutePath(),
                    "Xuất File Thành Công",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi khi lưu file CSV: " + ex.getMessage(),
                    "Lỗi Xuất File",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        return value;
    }
}
