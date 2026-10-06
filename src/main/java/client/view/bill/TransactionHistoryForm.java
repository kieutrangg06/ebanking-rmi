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
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Giao diện xem lịch sử giao dịch và xuất sao kê CSV thuần Java Swing (Người 2).
 * Tích hợp chuẩn UITheme, ModernButton, ModernTextField, RoundedPanel.
 * Sử dụng SwingWorker cho tất cả truy vấn RMI.
 */
public class TransactionHistoryForm extends JFrame {

    private final IBankService bankService;
    private final Account account;

    private ModernTextField txtFromDate;
    private ModernTextField txtToDate;
    private ModernButton btnFilter;
    private ModernButton btnAll;
    private ModernButton btnExportCsv;

    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;
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
        setTitle("Lịch Sử Giao Dịch & Sao Kê - " + account.getFullName() + " (STK: " + account.getAccountNumber() + ")");
        setSize(960, 640);
        setMinimumSize(new Dimension(860, 520));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(UITheme.BG_MAIN);

        JPanel mainPanel = new JPanel(new BorderLayout(14, 14));
        mainPanel.setBackground(UITheme.BG_MAIN);
        mainPanel.setBorder(new EmptyBorder(18, 20, 18, 20));

        // 1. TOP CONTAINER: TITLE + FILTER BAR
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(false);

        // Header Title
        JPanel headerPanel = new JPanel(new BorderLayout(10, 4));
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("LỊCH SỬ GIAO DỊCH & SAO KÊ TÀI KHOẢN");
        lblTitle.setFont(UITheme.FONT_TITLE_XL);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);

        JLabel lblSub = new JLabel("Chủ tài khoản: " + account.getFullName()
                + " | STK: " + account.getAccountNumber()
                + " | Số dư khả dụng: " + UITheme.MONEY_FORMAT.format(account.getBalance()));
        lblSub.setFont(UITheme.FONT_BODY);
        lblSub.setForeground(UITheme.TEXT_MUTED);

        headerPanel.add(lblTitle, BorderLayout.NORTH);
        headerPanel.add(lblSub, BorderLayout.SOUTH);
        topContainer.add(headerPanel);
        topContainer.add(Box.createVerticalStrut(12));

        // Filter Bar Card
        RoundedPanel filterCard = new RoundedPanel(12, Color.WHITE);
        filterCard.setLayout(new BorderLayout(10, 8));
        filterCard.setBorder(new EmptyBorder(12, 16, 12, 16));
        filterCard.setShowShadow(false);

        JPanel filterInputs = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterInputs.setOpaque(false);

        JLabel lblFrom = new JLabel("Từ ngày:");
        lblFrom.setFont(UITheme.FONT_BODY_BOLD);
        lblFrom.setForeground(UITheme.TEXT_MAIN);
        txtFromDate = new ModernTextField("yyyy-MM-dd");
        txtFromDate.setPreferredSize(new Dimension(115, 34));

        JLabel lblTo = new JLabel("Đến ngày:");
        lblTo.setFont(UITheme.FONT_BODY_BOLD);
        lblTo.setForeground(UITheme.TEXT_MAIN);
        txtToDate = new ModernTextField("yyyy-MM-dd");
        txtToDate.setPreferredSize(new Dimension(115, 34));

        btnFilter = new ModernButton("  Lọc Theo Ngày  ", ModernButton.Style.PRIMARY);
        btnFilter.setFont(UITheme.FONT_SMALL_BOLD);
        btnFilter.setCornerRadius(6);
        btnFilter.addActionListener(e -> performFilter());

        btnAll = new ModernButton("  Tất Cả  ", ModernButton.Style.SECONDARY);
        btnAll.setFont(UITheme.FONT_SMALL_BOLD);
        btnAll.setCornerRadius(6);
        btnAll.addActionListener(e -> {
            txtFromDate.setText("");
            txtToDate.setText("");
            loadTransactions(null, null);
        });

        filterInputs.add(lblFrom);
        filterInputs.add(txtFromDate);
        filterInputs.add(lblTo);
        filterInputs.add(txtToDate);
        filterInputs.add(btnFilter);
        filterInputs.add(btnAll);

        JPanel filterActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        filterActions.setOpaque(false);

        btnExportCsv = new ModernButton("  Xuất Sao Kê (CSV)  ", ModernButton.Style.SUCCESS);
        btnExportCsv.setFont(UITheme.FONT_SMALL_BOLD);
        btnExportCsv.setCornerRadius(6);
        btnExportCsv.addActionListener(e -> performExportCsv());
        filterActions.add(btnExportCsv);

        filterCard.add(filterInputs, BorderLayout.WEST);
        filterCard.add(filterActions, BorderLayout.EAST);
        topContainer.add(filterCard);

        mainPanel.add(topContainer, BorderLayout.NORTH);

        // 2. CENTER: TABLE
        String[] columns = {"STT", "Mã GD", "Loại Giao Dịch", "TK Gửi", "TK Nhận", "Số Tiền (VNĐ)", "Nội Dung Diễn Giải", "Thời Gian", "Trạng Thái"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Chỉ xem
            }
        };

        table = new JTable(tableModel);
        table.setFont(UITheme.FONT_BODY);
        table.setRowHeight(32);
        table.setGridColor(UITheme.BORDER_COLOR);
        table.setShowGrid(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(UITheme.PRIMARY_LIGHT);
        table.setSelectionForeground(UITheme.PRIMARY_DARK);

        JTableHeader header = table.getTableHeader();
        header.setFont(UITheme.FONT_BODY_BOLD);
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(UITheme.TEXT_MAIN);
        header.setPreferredSize(new Dimension(header.getWidth(), 36));

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(45);
        table.getColumnModel().getColumn(1).setPreferredWidth(55);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setPreferredWidth(110);
        table.getColumnModel().getColumn(6).setPreferredWidth(210);
        table.getColumnModel().getColumn(7).setPreferredWidth(140);
        table.getColumnModel().getColumn(8).setPreferredWidth(90);

        // Custom Renderers
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(7).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(8).setCellRenderer(centerRenderer);

        // Amount Renderer with colors
        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setFont(UITheme.FONT_BODY_BOLD);
                String valStr = value != null ? value.toString() : "";
                if (valStr.startsWith("+")) {
                    setForeground(isSelected ? UITheme.SUCCESS_DARK : UITheme.SUCCESS);
                } else if (valStr.startsWith("-")) {
                    setForeground(isSelected ? UITheme.DANGER_DARK : UITheme.DANGER);
                } else {
                    setForeground(UITheme.TEXT_MAIN);
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR));
        scrollPane.getViewport().setBackground(Color.WHITE);

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 3. BOTTOM: SUMMARY & STATUS
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 4));
        bottomPanel.setOpaque(false);

        lblSummary = new JLabel("Tổng số giao dịch: 0 | Tổng tiền chi: 0 VNĐ | Tổng tiền nhận: 0 VNĐ");
        lblSummary.setFont(UITheme.FONT_BODY_BOLD);
        lblSummary.setForeground(UITheme.PRIMARY_DARK);

        lblStatus = new JLabel("Sẵn sàng.", SwingConstants.RIGHT);
        lblStatus.setFont(UITheme.FONT_SMALL);
        lblStatus.setForeground(UITheme.TEXT_MUTED);

        bottomPanel.add(lblSummary, BorderLayout.WEST);
        bottomPanel.add(lblStatus, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
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

        // Strict format validation
        String dateRegex = "^\\d{4}-\\d{2}-\\d{2}$";

        if (!fromStr.isEmpty()) {
            if (!fromStr.matches(dateRegex)) {
                JOptionPane.showMessageDialog(this,
                        "Định dạng 'Từ ngày' không hợp lệ! Vui lòng nhập chuẩn yyyy-MM-dd (VD: 2026-09-01).",
                        "Sai Định Dạng Ngày", JOptionPane.WARNING_MESSAGE);
                txtFromDate.requestFocus();
                return;
            }
            try {
                fromDate = dateFormat.parse(fromStr);
            } catch (ParseException e) {
                JOptionPane.showMessageDialog(this,
                        "Ngày bắt đầu không tồn tại trong lịch thực tế (VD: ngày 30/02)!",
                        "Ngày Không Hợp Lệ", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        if (!toStr.isEmpty()) {
            if (!toStr.matches(dateRegex)) {
                JOptionPane.showMessageDialog(this,
                        "Định dạng 'Đến ngày' không hợp lệ! Vui lòng nhập chuẩn yyyy-MM-dd (VD: 2026-09-30).",
                        "Sai Định Dạng Ngày", JOptionPane.WARNING_MESSAGE);
                txtToDate.requestFocus();
                return;
            }
            try {
                toDate = dateFormat.parse(toStr);
            } catch (ParseException e) {
                JOptionPane.showMessageDialog(this,
                        "Ngày kết thúc không tồn tại trong lịch thực tế!",
                        "Ngày Không Hợp Lệ", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        if (fromDate != null && toDate != null && fromDate.after(toDate)) {
            JOptionPane.showMessageDialog(this,
                    "Khoảng ngày không hợp lệ: 'Từ ngày' không được lớn hơn 'Đến ngày'!",
                    "Lỗi Khoảng Thời Gian", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loadTransactions(fromDate, toDate);
    }

    private void loadTransactions(Date fromDate, Date toDate) {
        setLoadingState(true);
        lblStatus.setText("Đang tải dữ liệu từ Server...");

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
                setLoadingState(false);
                try {
                    List<Transaction> list = get();
                    currentTransactions.clear();
                    if (list != null) {
                        currentTransactions.addAll(list);
                    }
                    updateTableData(currentTransactions);
                    lblStatus.setText("Tải dữ liệu hoàn tất lúc " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
                } catch (InterruptedException | ExecutionException e) {
                    lblStatus.setText("Lỗi kết nối.");
                    JOptionPane.showMessageDialog(TransactionHistoryForm.this,
                            "Không thể tải lịch sử giao dịch: " + e.getMessage(),
                            "Lỗi Tải Dữ Liệu",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void updateTableData(List<Transaction> list) {
        tableModel.setRowCount(0);

        double totalIn = 0;
        double totalOut = 0;
        int stt = 1;

        for (Transaction tx : list) {
            String myAcc = account.getAccountNumber();
            boolean isSender = myAcc.equalsIgnoreCase(tx.getFromAccount());
            boolean isReceiver = myAcc.equalsIgnoreCase(tx.getToAccount());

            String typeDisplay = formatTransactionType(tx.getTransactionType(), isSender, isReceiver);
            String amountDisplay;

            if (isSender) {
                amountDisplay = "-" + String.format("%,.0f", tx.getAmount());
                totalOut += tx.getAmount();
            } else {
                amountDisplay = "+" + String.format("%,.0f", tx.getAmount());
                totalIn += tx.getAmount();
            }

            String timeStr = tx.getCreatedAt() != null ? timestampFormat.format(tx.getCreatedAt()) : "";

            tableModel.addRow(new Object[]{
                    stt++,
                    "#" + tx.getId(),
                    typeDisplay,
                    tx.getFromAccount() != null ? tx.getFromAccount() : "-",
                    tx.getToAccount() != null ? tx.getToAccount() : "-",
                    amountDisplay,
                    tx.getDescription() != null ? tx.getDescription() : "",
                    timeStr,
                    tx.getStatus()
            });
        }

        lblSummary.setText(String.format("Tổng số GD: %d  |  Tổng chi: %,.0f VNĐ  |  Tổng thu: %,.0f VNĐ",
                list.size(), totalOut, totalIn));
    }

    private String formatTransactionType(String type, boolean isSender, boolean isReceiver) {
        if ("THANH_TOAN_HOA_DON".equalsIgnoreCase(type)) {
            return "Thanh Toán Hóa Đơn";
        } else if ("CHUYEN_TIEN".equalsIgnoreCase(type)) {
            if (isSender) return "Chuyển Tiền Đi";
            if (isReceiver) return "Nhận Tiền Đến";
            return "Chuyển Tiền";
        } else if ("TIET_KIEM".equalsIgnoreCase(type)) {
            return "Gửi Tiết Kiệm";
        }
        return type != null ? type : "Giao Dịch";
    }

    private void setLoadingState(boolean loading) {
        btnFilter.setEnabled(!loading);
        btnAll.setEnabled(!loading);
        btnExportCsv.setEnabled(!loading);
    }

    private void performExportCsv() {
        if (currentTransactions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Không có dữ liệu giao dịch nào để xuất sao kê!", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Lưu Sao Kê Giao Dịch Dạng File CSV");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Tệp CSV (*.csv)", "csv"));
        String defaultFileName = "SaoKe_" + account.getAccountNumber() + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
        fileChooser.setSelectedFile(new File(defaultFileName));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".csv");
            }

            try (FileOutputStream fos = new FileOutputStream(fileToSave);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

                // UTF-8 BOM để Excel trên Windows hiển thị đúng font tiếng Việt không bị lỗi font
                fos.write(0xEF);
                fos.write(0xBB);
                fos.write(0xBF);

                // CSV Header
                osw.write("STT,Ma GD,Loai Giao Dich,Tai Khoan Gui,Tai Khoan Nhan,So Tien (VND),Dien Giai,Thoi Gian,Trang Thai\n");

                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    StringBuilder line = new StringBuilder();
                    for (int j = 0; j < tableModel.getColumnCount(); j++) {
                        Object val = tableModel.getValueAt(i, j);
                        String str = val != null ? val.toString() : "";
                        // Escape CSV
                        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                            str = "\"" + str.replace("\"", "\"\"") + "\"";
                        }
                        line.append(str);
                        if (j < tableModel.getColumnCount() - 1) {
                            line.append(",");
                        }
                    }
                    line.append("\n");
                    osw.write(line.toString());
                }

                osw.flush();
                JOptionPane.showMessageDialog(this,
                        "Xuất sao kê CSV thành công!\nĐường dẫn: " + fileToSave.getAbsolutePath(),
                        "Thành Công",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Lỗi khi ghi tệp CSV: " + ex.getMessage(),
                        "Lỗi Xuất Tệp",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
