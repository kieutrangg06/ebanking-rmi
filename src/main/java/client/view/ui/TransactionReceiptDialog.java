package client.view.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TransactionReceiptDialog extends JDialog {

    public TransactionReceiptDialog(Window parent, String fromAcc, String fromName,
                                    String toAcc, String toName, double amount, String description) {
        super(parent, "Biên Lai Giao Dịch Điện Tử", ModalityType.APPLICATION_MODAL);
        initComponents(fromAcc, fromName, toAcc, toName, amount, description);
    }

    private void initComponents(String fromAcc, String fromName, String toAcc, String toName, double amount, String description) {
        setSize(460, 560);
        setLocationRelativeTo(getParent());
        setResizable(false);
        getContentPane().setBackground(UITheme.BG_MAIN);
        setLayout(new BorderLayout());

        JPanel rootPanel = new JPanel(new BorderLayout(15, 15));
        rootPanel.setBackground(UITheme.BG_MAIN);
        rootPanel.setBorder(new EmptyBorder(20, 25, 20, 25));

        // Card chính chứa biên lai
        RoundedPanel receiptCard = new RoundedPanel(18, Color.WHITE);
        receiptCard.setLayout(new BoxLayout(receiptCard, BoxLayout.Y_AXIS));
        receiptCard.setBorder(new EmptyBorder(25, 25, 20, 25));
        receiptCard.setShowShadow(true);

        // Icon checkmark tròn màu xanh ngọc
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = 56;
                int x = (getWidth() - size) / 2;
                int y = 0;
                g2.setColor(UITheme.SUCCESS_LIGHT);
                g2.fillOval(x - 6, y - 6, size + 12, size + 12);
                g2.setColor(UITheme.SUCCESS);
                g2.fillOval(x, y, size, size);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Vẽ dấu checkmark
                g2.drawLine(x + 16, y + 29, x + 24, y + 38);
                g2.drawLine(x + 24, y + 38, x + 40, y + 18);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(200, 68);
            }
        };
        iconPanel.setOpaque(false);
        receiptCard.add(iconPanel);

        // Tiêu đề trạng thái
        JLabel lblStatus = new JLabel("CHUYỂN TIỀN THÀNH CÔNG");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblStatus.setForeground(UITheme.SUCCESS_DARK);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        receiptCard.add(lblStatus);

        receiptCard.add(Box.createVerticalStrut(6));

        // Số tiền chuyển
        JLabel lblAmount = new JLabel("-" + UITheme.MONEY_FORMAT.format(amount));
        lblAmount.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblAmount.setForeground(UITheme.TEXT_MAIN);
        lblAmount.setAlignmentX(Component.CENTER_ALIGNMENT);
        receiptCard.add(lblAmount);

        // Chữ tiền tiếng Việt
        long roundedAmt = Math.round(amount);
        JLabel lblWords = new JLabel(UITheme.toVietnameseCurrencyWords(roundedAmt));
        lblWords.setFont(UITheme.FONT_SMALL);
        lblWords.setForeground(UITheme.TEXT_MUTED);
        lblWords.setAlignmentX(Component.CENTER_ALIGNMENT);
        receiptCard.add(lblWords);

        receiptCard.add(Box.createVerticalStrut(15));

        // Đường kẻ nét đứt ngăn cách
        JSeparator sep = new JSeparator() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4}, 0);
                g2.setStroke(dashed);
                g2.setColor(UITheme.BORDER_COLOR);
                g2.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(350, 10);
            }
        };
        receiptCard.add(sep);
        receiptCard.add(Box.createVerticalStrut(10));

        // Bảng chi tiết giao dịch
        JPanel detailsPanel = new JPanel(new GridLayout(6, 1, 0, 8));
        detailsPanel.setOpaque(false);

        String refCode = "FT" + new SimpleDateFormat("yyMMddHHmmss").format(new Date()) + ((int)(Math.random() * 900) + 100);
        String timeStr = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss").format(new Date());

        detailsPanel.add(createRow("Mã giao dịch:", refCode));
        detailsPanel.add(createRow("Thời gian:", timeStr));
        detailsPanel.add(createRow("Tài khoản nguồn:", fromAcc + (fromName != null && !fromName.isEmpty() ? " (" + fromName + ")" : "")));
        detailsPanel.add(createRow("Tài khoản nhận:", toAcc));
        detailsPanel.add(createRow("Người thụ hưởng:", (toName != null && !toName.isEmpty()) ? toName.toUpperCase() : "Chưa xác định"));
        detailsPanel.add(createRow("Nội dung:", (description != null && !description.isEmpty()) ? description : "Chuyen tien"));

        receiptCard.add(detailsPanel);

        rootPanel.add(receiptCard, BorderLayout.CENTER);

        // Nút bấm phía dưới
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);

        ModernButton btnClose = new ModernButton("  Đóng Biên Lai  ", ModernButton.Style.PRIMARY);
        btnClose.addActionListener(e -> dispose());
        btnPanel.add(btnClose);

        rootPanel.add(btnPanel, BorderLayout.SOUTH);

        add(rootPanel);
    }

    private JPanel createRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        JLabel lbl = new JLabel(label);
        lbl.setFont(UITheme.FONT_BODY);
        lbl.setForeground(UITheme.TEXT_MUTED);

        JLabel val = new JLabel(value);
        val.setFont(UITheme.FONT_BODY_BOLD);
        val.setForeground(UITheme.TEXT_MAIN);
        val.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.EAST);
        return row;
    }
}
