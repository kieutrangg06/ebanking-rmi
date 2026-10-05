package client.view.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class ModernTextField extends JTextField {
    private String placeholder = "";
    private boolean isFocused = false;
    private final int radius = 8;

    public ModernTextField() {
        this("");
    }

    public ModernTextField(String placeholder) {
        super();
        this.placeholder = placeholder;
        setFont(UITheme.FONT_BODY);
        setForeground(UITheme.TEXT_MAIN);
        setBackground(UITheme.BG_INPUT);
        setCaretColor(UITheme.PRIMARY);
        setOpaque(false);
        setBorder(new EmptyBorder(9, 12, 9, 12));

        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                isFocused = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                isFocused = false;
                repaint();
            }
        });
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    public String getPlaceholder() {
        return placeholder;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Vẽ nền ô nhập
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

        // Vẽ viền (sáng xanh khi focus, xám nhạt khi thường)
        if (isFocused) {
            g2.setColor(UITheme.BORDER_FOCUS);
            g2.setStroke(new BasicStroke(1.5f));
        } else {
            g2.setColor(UITheme.BORDER_COLOR);
            g2.setStroke(new BasicStroke(1.0f));
        }
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

        super.paintComponent(g);

        // Vẽ placeholder nếu rỗng
        if (getText().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
            g2.setFont(getFont());
            g2.setColor(UITheme.TEXT_HINT);
            FontMetrics fm = g2.getFontMetrics();
            int x = getInsets().left;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, x, y);
        }

        g2.dispose();
    }
}
