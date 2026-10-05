package client.view.admin;

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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Màn hình Bảng Điều Khiển Quản Trị Viên (Dev 3 - Tiết kiệm & Admin).
 * Cho phép Giám sát mạng theo thời gian thực (Network Session Monitoring),
 * Kiểm soát tài khoản, Xem tổng tiền hệ thống, và thực hiện Lệnh điều khiển
 * cưỡng chế từ xa (Remote Revocation: Kick / Lock Account qua RMI Callback).
 */
public class AdminDashboardForm extends JFrame {

    private final IBankService bankService;
    private final Account adminAccount;

    // Các bảng dữ liệu
    private JTable tableOnlineUsers;
    private DefaultTableModel modelOnlineUsers;

    private JTable tableAllAccounts;
    private DefaultTableModel modelAllAccounts;

    // Nhãn thống kê tổng quan
    private JLabel lblTotalBalance;
    private JLabel lblOnlineCount;
    private JLabel lblTotalAccounts;

    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0 VNĐ");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Timer autoRefreshTimer;

    public AdminDashboardForm(IBankService bankService, Account adminAccount) {
        this.bankService = bankService;
        this.adminAccount = adminAccount;

        setTitle("🛡️ e-Banking - Bảng Điều Khiển Quản Trị Hệ Thống (Admin Control Center)");
        setSize(950, 650);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        loadAllData();
        startAutoRefresh();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        mainPanel.setBackground(new Color(245, 247, 250));

        // --- 1. HEADER & THỐNG KÊ TỔNG QUAN ---
        JPanel headerPanel = new JPanel(new BorderLayout(10, 10));
        headerPanel.setOpaque(false);

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JLabel lblTitle = new JLabel("🛡️ TRUNG TÂM GIÁM SÁT & ĐIỀU KHIỂN HỆ THỐNG EBANKING");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 17));
        lblTitle.setForeground(new Color(26, 35, 126));

        JLabel lblAdminInfo = new JLabel("Admin: " + (adminAccount != null ? adminAccount.getFullName() : "Administrator"));
        lblAdminInfo.setFont(new Font("Arial", Font.ITALIC, 12));
        lblAdminInfo.setForeground(new Color(100, 110, 120));

        titleBar.add(lblTitle, BorderLayout.WEST);
        titleBar.add(lblAdminInfo, BorderLayout.EAST);
        headerPanel.add(titleBar, BorderLayout.NORTH);

        // Các thẻ số liệu thống kê (Metric Cards)
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        statsPanel.setOpaque(false);
        statsPanel.setPreferredSize(new Dimension(0, 75));

        lblOnlineCount = new JLabel("0", JLabel.CENTER);
        JPanel cardOnline = createMetricCard("🌐 PHIÊN ĐANG ONLINE", lblOnlineCount, new Color(46, 204, 113));

        lblTotalAccounts = new JLabel("0", JLabel.CENTER);
        JPanel cardAccounts = createMetricCard("👥 TỔNG TÀI KHOẢN", lblTotalAccounts, new Color(52, 152, 219));

        lblTotalBalance = new JLabel("0 VNĐ", JLabel.CENTER);
        JPanel cardBalance = createMetricCard("💰 TỔNG TIỀN TOÀN HỆ THỐNG", lblTotalBalance, new Color(155, 89, 182));

        statsPanel.add(cardOnline);
        statsPanel.add(cardAccounts);
        statsPanel.add(cardBalance);
        headerPanel.add(statsPanel, BorderLayout.CENTER);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // --- 2. CÁC TAB CHỨC NĂNG CHÍNH ---
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 13));

        tabbedPane.addTab("🌐 Giám Sát Phiên Online (Real-time Kick)", createOnlineUsersPanel());
        tabbedPane.addTab("👥 Quản Lý Toàn Bộ Tài Khoản (Đóng Băng)", createAllAccountsPanel());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // --- 3. THANH ĐIỀU KHIỂN DƯỚI CÙNG ---
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 8));
        footerPanel.setOpaque(false);

        JCheckBox chkAutoRefresh = new JCheckBox("Tự động làm mới mỗi 5 giây", true);
        chkAutoRefresh.setFont(new Font("Arial", Font.PLAIN, 12));
        chkAutoRefresh.setOpaque(false);
        chkAutoRefresh.addActionListener(e -> {
            if (chkAutoRefresh.isSelected()) {
                startAutoRefresh();
            } else {
                if (autoRefreshTimer != null) autoRefreshTimer.stop();
            }
        });

        JButton btnManualRefresh = new JButton("🔄 Làm Mới Ngay");
        btnManualRefresh.setFont(new Font("Arial", Font.PLAIN, 12));
        btnManualRefresh.addActionListener(e -> loadAllData());

        footerPanel.add(chkAutoRefresh);
        footerPanel.add(btnManualRefresh);
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JLabel lblHeader = new JLabel(title);
        lblHeader.setFont(new Font("Arial", Font.BOLD, 11));
        lblHeader.setForeground(new Color(120, 130, 140));

        valueLabel.setFont(new Font("Arial", Font.BOLD, 16));
        valueLabel.setForeground(accentColor);

        card.add(lblHeader, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // TAB 1: GIÁM SÁT PHIÊN ONLINE & THỰC THI LỆNH KICK TỪ XA
    // =========================================================================
    private JPanel createOnlineUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel lblHint = new JLabel("Danh sách các máy khách đang mở kết nối RMI Callback tới Server. Chọn một tài khoản để điều khiển:");
        lblHint.setFont(new Font("Arial", Font.ITALIC, 12));
        lblHint.setForeground(new Color(100, 110, 120));
        panel.add(lblHint, BorderLayout.NORTH);

        String[] columns = {"STK", "Tên Đăng Nhập", "Họ Tên", "Số Dư Hiện Tại", "Trạng Thái CSDL", "Trạng Thái Kết Nối"};
        modelOnlineUsers = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tableOnlineUsers = new JTable(modelOnlineUsers);
        tableOnlineUsers.setRowHeight(28);
        tableOnlineUsers.setFont(new Font("Arial", Font.PLAIN, 12));
        tableOnlineUsers.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        tableOnlineUsers.getTableHeader().setBackground(new Color(236, 240, 241));
        tableOnlineUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);
        tableOnlineUsers.getColumnModel().getColumn(0).setCellRenderer(center);
        tableOnlineUsers.getColumnModel().getColumn(4).setCellRenderer(center);
        tableOnlineUsers.getColumnModel().getColumn(5).setCellRenderer(center);

        panel.add(new JScrollPane(tableOnlineUsers), BorderLayout.CENTER);

        // Thanh công cụ hành động Admin (Kick / Khóa & Kick)
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        actionPanel.setOpaque(false);

        JButton btnKick = new JButton("🚪 Kick Khỏi Hệ Thống (Đá Văng Phiên)");
        btnKick.setFont(new Font("Arial", Font.BOLD, 13));
        btnKick.setBackground(new Color(231, 76, 60));
        btnKick.setForeground(Color.WHITE);
        btnKick.setFocusPainted(false);

        JButton btnEmergencyFreeze = new JButton("🔒 Khóa & Kick Ngay Lập Tức");
        btnEmergencyFreeze.setFont(new Font("Arial", Font.BOLD, 13));
        btnEmergencyFreeze.setBackground(new Color(192, 57, 43));
        btnEmergencyFreeze.setForeground(Color.WHITE);
        btnEmergencyFreeze.setFocusPainted(false);

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
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tài khoản đang online để Kick!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String accNum = (String) modelOnlineUsers.getValueAt(row, 0);
        String fullName = (String) modelOnlineUsers.getValueAt(row, 2);

        String reason = JOptionPane.showInputDialog(this,
                "Nhập lý do đá văng tài khoản " + fullName + " (" + accNum + "):",
                "Phát hiện nghi vấn gian lận hoặc yêu cầu từ Quản trị viên");

        if (reason == null || reason.trim().isEmpty()) return;

        try {
            boolean ok = bankService.kickUser(accNum, reason.trim());
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Đã đá văng phiên làm việc của " + accNum + " thành công!\nClient nạn nhân đã nhận lệnh cưỡng chế forceLogout.",
                        "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                loadAllData();
            } else {
                JOptionPane.showMessageDialog(this, "Kick thất bại: Người dùng có thể đã mất kết nối trước đó.", "Lỗi", JOptionPane.ERROR_MESSAGE);
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
                "BẠN CÓ CHẮC CHẮN MUỐN KHÓA VÀ KICK TÀI KHOẢN " + fullName + " (" + accNum + ")?\n" +
                        "- Trạng thái tài khoản sẽ chuyển thành LOCKED trong Database.\n" +
                        "- Máy khách sẽ bị ngắt kết nối và đá văng về màn hình đăng nhập ngay lập tức.",
                "Cảnh Báo Khóa Tài Khoản", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        String reason = JOptionPane.showInputDialog(this,
                "Nhập lý do khóa tài khoản:",
                "Phat hien hanh vi bat thuong");

        if (reason == null || reason.trim().isEmpty()) reason = "Phat hien nghi van gian lan";

        try {
            boolean ok = bankService.lockAccount(accNum, reason.trim());
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Đã KHÓA và ĐÁ VĂNG tài khoản " + accNum + " thành công!\nTài khoản này không thể đăng nhập cho đến khi được Admin mở khóa.",
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
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        String[] columns = {"ID", "Số Tài Khoản", "Tên Đăng Nhập", "Họ Tên Chủ TK", "Số Dư", "Trạng Thái"};
        modelAllAccounts = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tableAllAccounts = new JTable(modelAllAccounts);
        tableAllAccounts.setRowHeight(28);
        tableAllAccounts.setFont(new Font("Arial", Font.PLAIN, 12));
        tableAllAccounts.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        tableAllAccounts.getTableHeader().setBackground(new Color(236, 240, 241));
        tableAllAccounts.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);
        tableAllAccounts.getColumnModel().getColumn(0).setCellRenderer(center);
        tableAllAccounts.getColumnModel().getColumn(1).setCellRenderer(center);
        tableAllAccounts.getColumnModel().getColumn(5).setCellRenderer(center);

        panel.add(new JScrollPane(tableAllAccounts), BorderLayout.CENTER);

        // Thanh thao tác (Khóa / Mở Khóa / Xem sổ tiết kiệm)
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        actionPanel.setOpaque(false);

        JButton btnViewSavings = new JButton("📋 Xem Sổ Tiết Kiệm Của TK");
        btnViewSavings.setFont(new Font("Arial", Font.PLAIN, 12));

        JButton btnLock = new JButton("❄️ Đóng Băng / Khóa (Lock)");
        btnLock.setFont(new Font("Arial", Font.BOLD, 12));
        btnLock.setBackground(new Color(230, 126, 34));
        btnLock.setForeground(Color.WHITE);
        btnLock.setFocusPainted(false);

        JButton btnUnlock = new JButton("🔓 Mở Khóa (Unlock)");
        btnUnlock.setFont(new Font("Arial", Font.BOLD, 12));
        btnUnlock.setBackground(new Color(39, 174, 96));
        btnUnlock.setForeground(Color.WHITE);
        btnUnlock.setFocusPainted(false);

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
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tài khoản để khóa!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String accNum = (String) modelAllAccounts.getValueAt(row, 1);
        String currentStatus = (String) modelAllAccounts.getValueAt(row, 5);

        if ("LOCKED".equalsIgnoreCase(currentStatus)) {
            JOptionPane.showMessageDialog(this, "Tài khoản " + accNum + " đã bị khóa trước đó!", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tài khoản để mở khóa!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String accNum = (String) modelAllAccounts.getValueAt(row, 1);
        String currentStatus = (String) modelAllAccounts.getValueAt(row, 5);

        if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
            JOptionPane.showMessageDialog(this, "Tài khoản " + accNum + " đang hoạt động bình thường!", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tài khoản để xem sổ tiết kiệm!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String accNum = (String) modelAllAccounts.getValueAt(row, 1);
        String fullName = (String) modelAllAccounts.getValueAt(row, 3);

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
            sb.append("\n==> Tổng tiền trong các sổ ACTIVE: ").append(currencyFormat.format(totalActive));

            JTextArea textArea = new JTextArea(sb.toString());
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(550, 250));

            JOptionPane.showMessageDialog(this, scrollPane, "Chi Tiết Sổ Tiết Kiệm", JOptionPane.INFORMATION_MESSAGE);
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
                // 1. Tải danh sách tài khoản
                List<Account> allAccounts = bankService.getAllAccounts();
                Map<String, Account> accountMap = allAccounts.stream()
                        .collect(Collectors.toMap(Account::getAccountNumber, a -> a, (k1, k2) -> k1));

                modelAllAccounts.setRowCount(0);
                for (Account a : allAccounts) {
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

    @Override
    public void dispose() {
        if (autoRefreshTimer != null) autoRefreshTimer.stop();
        super.dispose();
    }
}
