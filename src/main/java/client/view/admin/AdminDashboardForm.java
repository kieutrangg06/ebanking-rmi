package client.view.admin;

import client.view.auth.LoginForm;
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
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.rmi.RemoteException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Màn hình Bảng Điều Khiển Quản Trị Viên (Admin Operations & Security Center).
 * Cung cấp khả năng Giám sát phiên mạng thời gian thực (Real-time Session Monitoring),
 * Kiểm soát bảo mật tài khoản (Emergency Freeze / Unlock), và Lệnh điều khiển cưỡng chế
 * từ xa qua RMI Callback (Remote Revocation: Kick / Lock Account).
 */
public class AdminDashboardForm extends JFrame {

    private final IBankService bankService;
    private final Account adminAccount;

    // Bảng dữ liệu
    private JTable tableOnlineUsers;
    private DefaultTableModel modelOnlineUsers;

    private JTable tableAllAccounts;
    private DefaultTableModel modelAllAccounts;
    private TableRowSorter<DefaultTableModel> accountSorter;
    private ModernTextField txtSearchAccount;

    // Nhãn KPI số liệu thống kê
    private JLabel lblTotalBalance;
    private JLabel lblOnlineCount;
    private JLabel lblTotalAccounts;
    private JLabel lblLockedAccounts;

    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0 VNĐ");
    private Timer autoRefreshTimer;

    public AdminDashboardForm(IBankService bankService, Account adminAccount) {
        this.bankService = bankService;
        this.adminAccount = adminAccount;

        setTitle("🛡️ e-Banking - Trung Tâm Giám Sát & Quản Trị Hệ Thống (Security Operations Center)");
        setSize(1040, 720);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        loadAllData();
        startAutoRefresh();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(14, 14));
        mainPanel.setBorder(new EmptyBorder(16, 18, 16, 18));
        mainPanel.setBackground(UITheme.BG_MAIN);

        // =====================================================================
        // 1. HEADER & KHỐI KPI CHỈ SỐ HỆ THỐNG
        // =====================================================================
        JPanel topContainer = new JPanel(new BorderLayout(12, 12));
        topContainer.setOpaque(false);

        // Thanh Title Bar
        RoundedPanel titleBar = new RoundedPanel(16, Color.WHITE);
        titleBar.setLayout(new BorderLayout(10, 10));
        titleBar.setBorder(new EmptyBorder(14, 20, 14, 20));
        titleBar.setShowShadow(true);

        JPanel titleLeft = new JPanel(new BorderLayout(4, 4));
        titleLeft.setOpaque(false);

        JLabel lblTitle = new JLabel("🛡️ TRUNG TÂM ĐIỀU HÀNH & AN NINH HỆ THỐNG");
        lblTitle.setFont(UITheme.FONT_TITLE_XL);
        lblTitle.setForeground(UITheme.PRIMARY_DARK);

        JLabel lblSub = new JLabel("Giám sát phiên làm việc RMI phân tán, quản trị an toàn thông tin và kiểm soát rủi ro ngân hàng.");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_MUTED);

        titleLeft.add(lblTitle, BorderLayout.NORTH);
        titleLeft.add(lblSub, BorderLayout.SOUTH);
        titleBar.add(titleLeft, BorderLayout.WEST);

        // Thông tin Admin & Nút Đăng xuất bên phải
        JPanel adminInfoBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        adminInfoBox.setOpaque(false);

        RoundedPanel badge = new RoundedPanel(10, UITheme.PRIMARY_LIGHT);
        badge.setLayout(new GridLayout(2, 1, 0, 2));
        badge.setBorder(new EmptyBorder(6, 14, 6, 14));

        JLabel lblAdminName = new JLabel("Admin: " + (adminAccount != null ? adminAccount.getFullName() : "Administrator"));
        lblAdminName.setFont(UITheme.FONT_BODY_BOLD);
        lblAdminName.setForeground(UITheme.PRIMARY);

        JLabel lblRole = new JLabel("🟢 QUẢN TRỊ VIÊN HỆ THỐNG");
        lblRole.setFont(UITheme.FONT_SMALL_BOLD);
        lblRole.setForeground(UITheme.SUCCESS_DARK);

        badge.add(lblAdminName);
        badge.add(lblRole);
        adminInfoBox.add(badge);

        ModernButton btnLogout = new ModernButton("Đăng Xuất", ModernButton.Style.DANGER);
        btnLogout.setFont(UITheme.FONT_SMALL_BOLD);
        btnLogout.setMargin(new Insets(8, 14, 8, 14));
        btnLogout.addActionListener(e -> performAdminLogout());
        adminInfoBox.add(btnLogout);

        titleBar.add(adminInfoBox, BorderLayout.EAST);
        topContainer.add(titleBar, BorderLayout.NORTH);

        // 4 Thẻ KPI Metrics Card
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 14, 0));
        statsPanel.setOpaque(false);
        statsPanel.setPreferredSize(new Dimension(0, 80));

        lblTotalAccounts = new JLabel("0", JLabel.LEFT);
        JPanel cardAccounts = createMetricCard("👥 TỔNG TÀI KHOẢN", "CSDL Khách hàng", lblTotalAccounts, UITheme.PRIMARY);

        lblOnlineCount = new JLabel("0", JLabel.LEFT);
        JPanel cardOnline = createMetricCard("🌐 PHIÊN ĐANG ONLINE", "RMI Callback Live", lblOnlineCount, UITheme.SUCCESS);

        lblTotalBalance = new JLabel("0 VNĐ", JLabel.LEFT);
        JPanel cardBalance = createMetricCard("💰 TỔNG TIỀN HỆ THỐNG", "Tài sản lưu ký", lblTotalBalance, UITheme.PRIMARY_DARK);

        lblLockedAccounts = new JLabel("0", JLabel.LEFT);
        JPanel cardLocked = createMetricCard("❄️ TÀI KHOẢN BỊ KHÓA", "Đang đóng băng", lblLockedAccounts, UITheme.DANGER);

        statsPanel.add(cardAccounts);
        statsPanel.add(cardOnline);
        statsPanel.add(cardBalance);
        statsPanel.add(cardLocked);
        topContainer.add(statsPanel, BorderLayout.CENTER);

        mainPanel.add(topContainer, BorderLayout.NORTH);

        // =====================================================================
        // 2. CÁC TAB CHỨC NĂNG CHÍNH
        // =====================================================================
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY_BOLD);

        tabbedPane.addTab("🌐 Giám Sát Phiên Online (Real-time Kick)", createOnlineUsersPanel());
        tabbedPane.addTab("👥 Quản Trị Danh Sách Tài Khoản & An Ninh", createAllAccountsPanel());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // =====================================================================
        // 3. THANH FOOTER ĐIỀU KHIỂN DƯỚI CÙNG
        // =====================================================================
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 6));
        footerPanel.setOpaque(false);

        JCheckBox chkAutoRefresh = new JCheckBox("Tự động quét và làm mới mỗi 5 giây", true);
        chkAutoRefresh.setFont(UITheme.FONT_BODY);
        chkAutoRefresh.setOpaque(false);
        chkAutoRefresh.addActionListener(e -> {
            if (chkAutoRefresh.isSelected()) {
                startAutoRefresh();
            } else {
                if (autoRefreshTimer != null) autoRefreshTimer.stop();
            }
        });

        ModernButton btnManualRefresh = new ModernButton("🔄 Quét Dữ Liệu Ngay", ModernButton.Style.SECONDARY);
        btnManualRefresh.setFont(UITheme.FONT_SMALL_BOLD);
        btnManualRefresh.setPreferredSize(new Dimension(160, 34));
        btnManualRefresh.addActionListener(e -> loadAllData());

        footerPanel.add(chkAutoRefresh);
        footerPanel.add(btnManualRefresh);
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createMetricCard(String title, String subtitle, JLabel valueLabel, Color accentColor) {
        RoundedPanel card = new RoundedPanel(14, Color.WHITE);
        card.setLayout(new BorderLayout(4, 4));
        card.setBorder(new EmptyBorder(10, 16, 10, 16));
        card.setShowShadow(true);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel lblHeader = new JLabel(title);
        lblHeader.setFont(UITheme.FONT_SMALL_BOLD);
        lblHeader.setForeground(accentColor);

        JLabel lblSub = new JLabel(subtitle);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblSub.setForeground(UITheme.TEXT_HINT);

        header.add(lblHeader, BorderLayout.NORTH);
        header.add(lblSub, BorderLayout.SOUTH);

        valueLabel.setFont(UITheme.FONT_TITLE_XL);
        valueLabel.setForeground(accentColor);

        card.add(header, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // TAB 1: GIÁM SÁT PHIÊN ONLINE & THỰC THI LỆNH KICK TỪ XA
    // =========================================================================
    private JPanel createOnlineUsersPanel() {
        RoundedPanel panel = new RoundedPanel(16, Color.WHITE);
        panel.setLayout(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));
        panel.setShowShadow(true);

        JPanel topBox = new JPanel(new BorderLayout(4, 4));
        topBox.setOpaque(false);
        JLabel lblHeader = new JLabel("DANH SÁCH MÁY KHÁCH ĐANG DUY TRÌ PHIÊN KẾT NỐI RMI CALLBACK");
        lblHeader.setFont(UITheme.FONT_SUBTITLE);
        lblHeader.setForeground(UITheme.PRIMARY_DARK);

        JLabel lblHint = new JLabel("Máy chủ quản lý các phiên thông qua kênh ConcurrentHashMap. Bạn có thể gửi lệnh hủy phiên tức thời.");
        lblHint.setFont(UITheme.FONT_SMALL);
        lblHint.setForeground(UITheme.TEXT_MUTED);

        topBox.add(lblHeader, BorderLayout.NORTH);
        topBox.add(lblHint, BorderLayout.SOUTH);
        panel.add(topBox, BorderLayout.NORTH);

        String[] columns = {"STK", "Tên Đăng Nhập", "Họ Tên Khách Hàng", "Số Dư Hiện Tại", "Trạng Thái CSDL", "Trạng Thái Phiên"};
        modelOnlineUsers = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tableOnlineUsers = new JTable(modelOnlineUsers);
        tableOnlineUsers.setRowHeight(32);
        tableOnlineUsers.setFont(UITheme.FONT_BODY);
        tableOnlineUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableOnlineUsers.setGridColor(UITheme.BORDER_COLOR);
        tableOnlineUsers.setShowVerticalLines(false);

        tableOnlineUsers.getTableHeader().setFont(UITheme.FONT_BODY_BOLD);
        tableOnlineUsers.getTableHeader().setBackground(UITheme.PRIMARY_LIGHT);
        tableOnlineUsers.getTableHeader().setForeground(UITheme.PRIMARY_DARK);
        tableOnlineUsers.getTableHeader().setPreferredSize(new Dimension(0, 36));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(JLabel.RIGHT);

        tableOnlineUsers.getColumnModel().getColumn(0).setCellRenderer(center);
        tableOnlineUsers.getColumnModel().getColumn(3).setCellRenderer(right);
        tableOnlineUsers.getColumnModel().getColumn(4).setCellRenderer(center);

        // Render Cột Kết Nối (Pill Xanh Online)
        tableOnlineUsers.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                lbl.setHorizontalAlignment(JLabel.CENTER);
                lbl.setFont(UITheme.FONT_SMALL_BOLD);
                lbl.setText("🟢 CONNECTED");
                lbl.setForeground(UITheme.SUCCESS_DARK);
                return lbl;
            }
        });

        JScrollPane scroll = new JScrollPane(tableOnlineUsers);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 1));
        panel.add(scroll, BorderLayout.CENTER);

        // Thanh công cụ hành động Admin (Kick / Khóa & Kick)
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
        actionPanel.setOpaque(false);

        ModernButton btnKick = new ModernButton("🚪 Kick Phiên (Đá Văng Khỏi Hệ Thống)", ModernButton.Style.DANGER);
        btnKick.setFont(UITheme.FONT_BODY_BOLD);
        btnKick.setPreferredSize(new Dimension(280, 38));

        ModernButton btnEmergencyFreeze = new ModernButton("🔒 Khóa Khẩn Cấp & Kick Ngay Lập Tức", ModernButton.Style.DANGER);
        btnEmergencyFreeze.setFont(UITheme.FONT_BODY_BOLD);
        btnEmergencyFreeze.setPreferredSize(new Dimension(290, 38));

        actionPanel.add(btnKick);
        actionPanel.add(btnEmergencyFreeze);
        panel.add(actionPanel, BorderLayout.SOUTH);

        btnKick.addActionListener(e -> handleKickUser());
        btnEmergencyFreeze.addActionListener(e -> handleEmergencyLockAndKick());

        return panel;
    }

    private void handleKickUser() {
        int row = tableOnlineUsers.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tài khoản đang online trong bảng để thực hiện lệnh Kick!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String accNum = (String) modelOnlineUsers.getValueAt(row, 0);
        String fullName = (String) modelOnlineUsers.getValueAt(row, 2);

        String reason = JOptionPane.showInputDialog(this,
                "Nhập lý do đá văng tài khoản " + fullName + " (" + accNum + "):\n(Lý do này sẽ hiển thị trên màn hình của Client)",
                "Phát hiện nghi vấn thao tác bất thường");

        if (reason == null || reason.trim().isEmpty()) return;

        try {
            boolean ok = bankService.kickUser(accNum, reason.trim());
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Đã đá văng phiên làm việc của STK " + accNum + " thành công!\n" +
                                "Máy khách đã lập tức nhận lệnh cưỡng chế forceLogout qua RMI Callback.",
                        "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                loadAllData();
            } else {
                JOptionPane.showMessageDialog(this, "Kick thất bại: Máy khách có thể đã ngắt kết nối trước đó.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối RMI: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void handleEmergencyLockAndKick() {
        int row = tableOnlineUsers.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tài khoản đang online để Khóa & Kick!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String accNum = (String) modelOnlineUsers.getValueAt(row, 0);
        String fullName = (String) modelOnlineUsers.getValueAt(row, 2);

        int confirm = JOptionPane.showConfirmDialog(this,
                "BẠN CÓ CHẮC CHẮN MUỐN KHÓA VÀ KICK TÀI KHOẢN NÀY?\n\n" +
                        "• Chủ tài khoản: " + fullName + " (STK: " + accNum + ")\n" +
                        "• Hậu quả 1: Trạng thái tài khoản sẽ chuyển thành 'LOCKED' trong Database.\n" +
                        "• Hậu quả 2: Máy khách bị ngắt kết nối và đá văng về màn hình đăng nhập ngay.\n" +
                        "• Hậu quả 3: Chặn đăng nhập lại cho đến khi được Admin mở khóa.",
                "Cảnh Báo An Ninh Khẩn Cấp", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        String reason = JOptionPane.showInputDialog(this,
                "Nhập lý do đóng băng tài khoản:",
                "Phat hien hanh vi bat thuong hoac nghi van gian lan");

        if (reason == null || reason.trim().isEmpty()) reason = "Phat hien hanh vi bat thuong";

        try {
            boolean ok = bankService.lockAccount(accNum, reason.trim());
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Đã KHÓA và ĐÁ VĂNG tài khoản STK " + accNum + " thành công!\n" +
                                "Tài khoản hiện đang ở trạng thái LOCKED và bị từ chối mọi giao dịch.",
                        "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                loadAllData();
            } else {
                JOptionPane.showMessageDialog(this, "Khóa tài khoản thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối RMI: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    // =========================================================================
    // TAB 2: QUẢN LÝ TẤT CẢ TÀI KHOẢN TRONG CSDL (ĐÓNG BĂNG / MỞ KHÓA)
    // =========================================================================
    private JPanel createAllAccountsPanel() {
        RoundedPanel panel = new RoundedPanel(16, Color.WHITE);
        panel.setLayout(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));
        panel.setShowShadow(true);

        // Header Tab 2 & Khung Tìm kiếm
        JPanel topBox = new JPanel(new BorderLayout(10, 10));
        topBox.setOpaque(false);

        JPanel titleGroup = new JPanel(new BorderLayout(3, 3));
        titleGroup.setOpaque(false);
        JLabel lblHeader = new JLabel("QUẢN TRỊ DANH SÁCH TÀI KHOẢN & AN NINH DỮ LIỆU");
        lblHeader.setFont(UITheme.FONT_SUBTITLE);
        lblHeader.setForeground(UITheme.PRIMARY_DARK);

        JLabel lblHint = new JLabel("Tra cứu, xem thông tin sổ tiết kiệm và thực hiện Đóng Băng / Mở Khóa tài khoản.");
        lblHint.setFont(UITheme.FONT_SMALL);
        lblHint.setForeground(UITheme.TEXT_MUTED);

        titleGroup.add(lblHeader, BorderLayout.NORTH);
        titleGroup.add(lblHint, BorderLayout.SOUTH);
        topBox.add(titleGroup, BorderLayout.WEST);

        // Ô tìm kiếm nhanh
        JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchBox.setOpaque(false);
        JLabel lblSearch = new JLabel("🔍 Tra cứu:");
        lblSearch.setFont(UITheme.FONT_BODY_BOLD);
        txtSearchAccount = new ModernTextField("Tìm theo STK, Tên, Username...");
        txtSearchAccount.setPreferredSize(new Dimension(220, 36));
        searchBox.add(lblSearch);
        searchBox.add(txtSearchAccount);
        topBox.add(searchBox, BorderLayout.EAST);

        panel.add(topBox, BorderLayout.NORTH);

        String[] columns = {"ID", "Số Tài Khoản", "Tên Đăng Nhập", "Họ Tên Chủ TK", "Số Dư Hiện Tại", "Trạng Thái CSDL"};
        modelAllAccounts = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        accountSorter = new TableRowSorter<>(modelAllAccounts);
        tableAllAccounts = new JTable(modelAllAccounts);
        tableAllAccounts.setRowSorter(accountSorter);
        tableAllAccounts.setRowHeight(32);
        tableAllAccounts.setFont(UITheme.FONT_BODY);
        tableAllAccounts.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableAllAccounts.setGridColor(UITheme.BORDER_COLOR);
        tableAllAccounts.setShowVerticalLines(false);

        tableAllAccounts.getTableHeader().setFont(UITheme.FONT_BODY_BOLD);
        tableAllAccounts.getTableHeader().setBackground(UITheme.PRIMARY_LIGHT);
        tableAllAccounts.getTableHeader().setForeground(UITheme.PRIMARY_DARK);
        tableAllAccounts.getTableHeader().setPreferredSize(new Dimension(0, 36));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(JLabel.RIGHT);

        tableAllAccounts.getColumnModel().getColumn(0).setCellRenderer(center);
        tableAllAccounts.getColumnModel().getColumn(1).setCellRenderer(center);
        tableAllAccounts.getColumnModel().getColumn(4).setCellRenderer(right);

        // Render Cột Trạng thái (Pill ACTIVE / LOCKED)
        tableAllAccounts.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                lbl.setHorizontalAlignment(JLabel.CENTER);
                lbl.setFont(UITheme.FONT_SMALL_BOLD);
                String st = val != null ? val.toString() : "";
                if ("ACTIVE".equalsIgnoreCase(st)) {
                    lbl.setText("🟢 ACTIVE");
                    lbl.setForeground(UITheme.SUCCESS_DARK);
                } else if ("LOCKED".equalsIgnoreCase(st)) {
                    lbl.setText("🔒 LOCKED");
                    lbl.setForeground(UITheme.DANGER);
                } else {
                    lbl.setText(st);
                    lbl.setForeground(UITheme.TEXT_MUTED);
                }
                return lbl;
            }
        });

        JScrollPane scroll = new JScrollPane(tableAllAccounts);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_COLOR, 1));
        panel.add(scroll, BorderLayout.CENTER);

        // Bộ lọc tìm kiếm nhanh
        txtSearchAccount.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
            private void applyFilter() {
                String text = txtSearchAccount.getText().trim();
                if (text.isEmpty()) {
                    accountSorter.setRowFilter(null);
                } else {
                    accountSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
                }
            }
        });

        // Thanh thao tác (Khóa / Mở Khóa / Xem sổ tiết kiệm)
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
        actionPanel.setOpaque(false);

        ModernButton btnViewSavings = new ModernButton("📋 Xem Sổ Tiết Kiệm Của TK", ModernButton.Style.SECONDARY);
        btnViewSavings.setPreferredSize(new Dimension(220, 38));

        ModernButton btnLock = new ModernButton("❄️ Đóng Băng / Khóa (Lock)", ModernButton.Style.DANGER);
        btnLock.setPreferredSize(new Dimension(210, 38));

        ModernButton btnUnlock = new ModernButton("🔓 Mở Khóa Tài Khoản (Unlock)", ModernButton.Style.SUCCESS);
        btnUnlock.setPreferredSize(new Dimension(230, 38));

        actionPanel.add(btnViewSavings);
        actionPanel.add(btnLock);
        actionPanel.add(btnUnlock);
        panel.add(actionPanel, BorderLayout.SOUTH);

        btnLock.addActionListener(e -> handleLockSelectedAccount());
        btnUnlock.addActionListener(e -> handleUnlockSelectedAccount());
        btnViewSavings.addActionListener(e -> handleViewUserSavings());

        return panel;
    }

    private void handleLockSelectedAccount() {
        int row = tableAllAccounts.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tài khoản trong bảng để khóa!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = tableAllAccounts.convertRowIndexToModel(row);
        String accNum = (String) modelAllAccounts.getValueAt(modelRow, 1);
        String currentStatus = (String) modelAllAccounts.getValueAt(modelRow, 5);

        if ("LOCKED".equalsIgnoreCase(currentStatus)) {
            JOptionPane.showMessageDialog(this, "Tài khoản " + accNum + " hiện đã ở trạng thái LOCKED!", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String reason = JOptionPane.showInputDialog(this, "Nhập lý do khóa tài khoản " + accNum + ":", "Yêu cầu từ Quản trị viên");
        if (reason == null || reason.trim().isEmpty()) reason = "Admin khoa tai khoan";

        try {
            boolean ok = bankService.lockAccount(accNum, reason.trim());
            if (ok) {
                JOptionPane.showMessageDialog(this, "Đã khóa thành công tài khoản " + accNum);
                loadAllData();
            } else {
                JOptionPane.showMessageDialog(this, "Khóa tài khoản thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối RMI: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleUnlockSelectedAccount() {
        int row = tableAllAccounts.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tài khoản trong bảng để mở khóa!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = tableAllAccounts.convertRowIndexToModel(row);
        String accNum = (String) modelAllAccounts.getValueAt(modelRow, 1);
        String currentStatus = (String) modelAllAccounts.getValueAt(modelRow, 5);

        if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
            JOptionPane.showMessageDialog(this, "Tài khoản " + accNum + " đang hoạt động bình thường (ACTIVE)!", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            boolean ok = bankService.unlockAccount(accNum);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Đã mở khóa thành công tài khoản " + accNum + "!");
                loadAllData();
            } else {
                JOptionPane.showMessageDialog(this, "Mở khóa thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối RMI: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleViewUserSavings() {
        int row = tableAllAccounts.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tài khoản trong bảng để xem sổ tiết kiệm!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = tableAllAccounts.convertRowIndexToModel(row);
        String accNum = (String) modelAllAccounts.getValueAt(modelRow, 1);
        String fullName = (String) modelAllAccounts.getValueAt(modelRow, 3);

        try {
            List<Saving> savings = bankService.getSavingsByAccount(accNum);
            if (savings.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Tài khoản " + fullName + " (" + accNum + ") hiện không có sổ tiết kiệm nào.", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            StringBuilder sb = new StringBuilder("DANH SÁCH SỔ TIẾT KIỆM CỦA: " + fullName + " (" + accNum + ")\n\n");
            double totalActive = 0.0;
            for (Saving s : savings) {
                double total = s.getDepositAmount() + s.getAccumulatedInterest();
                if ("ACTIVE".equalsIgnoreCase(s.getStatus())) totalActive += total;
                sb.append("• Sổ #").append(s.getId())
                  .append(" | Gốc: ").append(currencyFormat.format(s.getDepositAmount()))
                  .append(" | Lãi: ").append(currencyFormat.format(s.getAccumulatedInterest()))
                  .append(" | Kỳ hạn: ").append(s.getTermPeriod()).append("s (").append(s.getInterestRate()).append("%)")
                  .append(" | Trạng thái: ").append(s.getStatus()).append("\n");
            }
            sb.append("\n==> TỔNG TIỀN TRONG CÁC SỔ ACTIVE: ").append(currencyFormat.format(totalActive));

            JTextArea textArea = new JTextArea(sb.toString());
            textArea.setEditable(false);
            textArea.setFont(new Font("Consolas", Font.PLAIN, 13));
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(580, 260));

            JOptionPane.showMessageDialog(this, scrollPane, "Chi Tiết Sổ Tiết Kiệm - " + accNum, JOptionPane.INFORMATION_MESSAGE);
        } catch (RemoteException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối RMI: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // DỮ LIỆU & TỰ ĐỘNG CẬP NHẬT
    // =========================================================================
    public void loadAllData() {
        SwingUtilities.invokeLater(() -> {
            try {
                // 1. Tải danh sách tất cả tài khoản
                List<Account> allAccounts = bankService.getAllAccounts();
                Map<String, Account> accountMap = allAccounts.stream()
                        .collect(Collectors.toMap(Account::getAccountNumber, a -> a, (k1, k2) -> k1));

                modelAllAccounts.setRowCount(0);
                long lockedCount = 0;
                for (Account a : allAccounts) {
                    if ("LOCKED".equalsIgnoreCase(a.getStatus())) {
                        lockedCount++;
                    }
                    modelAllAccounts.addRow(new Object[]{
                            a.getId(),
                            a.getAccountNumber(),
                            a.getUsername(),
                            a.getFullName(),
                            currencyFormat.format(a.getBalance()),
                            a.getStatus()
                    });
                }
                lblTotalAccounts.setText(String.valueOf(allAccounts.size()));
                lblLockedAccounts.setText(String.valueOf(lockedCount));

                // 2. Tải danh sách online
                List<String> onlines = bankService.getOnlineUsers();
                modelOnlineUsers.setRowCount(0);
                for (String accNum : onlines) {
                    Account acc = accountMap.get(accNum);
                    String user = (acc != null) ? acc.getUsername() : "Chưa rõ";
                    String name = (acc != null) ? acc.getFullName() : "Khách";
                    String bal = (acc != null) ? currencyFormat.format(acc.getBalance()) : "N/A";
                    String st = (acc != null) ? acc.getStatus() : "ACTIVE";

                    modelOnlineUsers.addRow(new Object[]{
                            accNum, user, name, bal, st, "🟢 CONNECTED"
                    });
                }
                lblOnlineCount.setText(String.valueOf(onlines.size()));

                // 3. Tải tổng tiền hệ thống
                double totalBalance = bankService.getTotalSystemBalance();
                lblTotalBalance.setText(currencyFormat.format(totalBalance));

            } catch (RemoteException e) {
                System.err.println("Lỗi khi tải dữ liệu Admin Dashboard: " + e.getMessage());
            }
        });
    }

    private void startAutoRefresh() {
        if (autoRefreshTimer != null) autoRefreshTimer.stop();
        autoRefreshTimer = new Timer(5000, e -> loadAllData());
        autoRefreshTimer.start();
    }

    private void performAdminLogout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn đăng xuất khỏi Bảng Điều Khiển Quản Trị?",
                "Xác Nhận Đăng Xuất", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            dispose();
            new LoginForm().setVisible(true);
        }
    }

    @Override
    public void dispose() {
        if (autoRefreshTimer != null) autoRefreshTimer.stop();
        super.dispose();
    }
}
