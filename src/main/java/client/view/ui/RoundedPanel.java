package client.view.ui;

import javax.swing.*;
import java.awt.*;

public class RoundedPanel extends JPanel {
    private int cornerRadius = 16;
    private Color backgroundColor = UITheme.BG_CARD;
    private Color gradientEndColor = null;
    private Color borderColor = null;
    private int borderThickness = 1;
    private boolean showShadow = false;

    public RoundedPanel() {
        super();
        setOpaque(false);
    }

    public RoundedPanel(int radius) {
        super();
        this.cornerRadius = radius;
        setOpaque(false);
    }

    public RoundedPanel(int radius, Color bg) {
        super();
        this.cornerRadius = radius;
        this.backgroundColor = bg;
        setOpaque(false);
    }

    public RoundedPanel(int radius, Color gradientStart, Color gradientEnd) {
        super();
        this.cornerRadius = radius;
        this.backgroundColor = gradientStart;
        this.gradientEndColor = gradientEnd;
        setOpaque(false);
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    public void setBackgroundColor(Color bg) {
        this.backgroundColor = bg;
        repaint();
    }

    public void setGradientColors(Color start, Color end) {
        this.backgroundColor = start;
        this.gradientEndColor = end;
        repaint();
    }

    public void setBorderColor(Color color, int thickness) {
        this.borderColor = color;
        this.borderThickness = thickness;
        repaint();
    }

    public void setShowShadow(boolean showShadow) {
        this.showShadow = showShadow;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int shadowOffset = showShadow ? 3 : 0;
        int drawW = width - shadowOffset - 1;
        int drawH = height - shadowOffset - 1;

        // Vẽ bóng đổ nhẹ nếu được bật
        if (showShadow) {
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(shadowOffset, shadowOffset, drawW, drawH, cornerRadius, cornerRadius);
            g2.setColor(new Color(0, 0, 0, 8));
            g2.fillRoundRect(shadowOffset + 1, shadowOffset + 1, drawW, drawH, cornerRadius, cornerRadius);
        }

        // Vẽ nền (đơn sắc hoặc gradient)
        if (gradientEndColor != null) {
            GradientPaint gp = new GradientPaint(0, 0, backgroundColor, drawW, drawH, gradientEndColor);
            g2.setPaint(gp);
        } else {
            g2.setColor(backgroundColor != null ? backgroundColor : getBackground());
        }
        g2.fillRoundRect(0, 0, drawW, drawH, cornerRadius, cornerRadius);

        // Vẽ viền
        if (borderColor != null && borderThickness > 0) {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderThickness));
            g2.drawRoundRect(0, 0, drawW, drawH, cornerRadius, cornerRadius);
        }

        g2.dispose();
    }
}
