package client.view.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ModernButton extends JButton {
    public enum Style {
        PRIMARY, SUCCESS, DANGER, OUTLINE, SECONDARY
    }

    private Style style = Style.PRIMARY;
    private int cornerRadius = 8;
    private boolean isHovered = false;
    private boolean isPressed = false;

    public ModernButton(String text) {
        this(text, Style.PRIMARY);
    }

    public ModernButton(String text, Style style) {
        super(text);
        this.style = style;
        initButton();
    }

    private void initButton() {
        setFont(UITheme.FONT_BODY_BOLD);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(10, 18, 10, 18));

        updateStyleColors();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                isPressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = false;
                    repaint();
                }
            }
        });
    }

    public void setStyle(Style style) {
        this.style = style;
        updateStyleColors();
        repaint();
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    private void updateStyleColors() {
        switch (style) {
            case PRIMARY:
            case SUCCESS:
            case DANGER:
                setForeground(Color.WHITE);
                break;
            case OUTLINE:
                setForeground(UITheme.PRIMARY);
                break;
            case SECONDARY:
                setForeground(UITheme.TEXT_MAIN);
                break;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        Color baseColor;
        Color hoverColor;
        Color pressedColor;

        switch (style) {
            case SUCCESS:
                baseColor = UITheme.SUCCESS;
                hoverColor = new Color(5, 150, 105);
                pressedColor = UITheme.SUCCESS_DARK;
                break;
            case DANGER:
                baseColor = UITheme.DANGER;
                hoverColor = new Color(220, 38, 38);
                pressedColor = UITheme.DANGER_DARK;
                break;
            case OUTLINE:
                baseColor = isHovered ? UITheme.PRIMARY_LIGHT : Color.WHITE;
                hoverColor = UITheme.PRIMARY_LIGHT;
                pressedColor = new Color(219, 234, 254);
                break;
            case SECONDARY:
                baseColor = new Color(241, 245, 249);
                hoverColor = new Color(226, 232, 240);
                pressedColor = new Color(203, 213, 225);
                break;
            case PRIMARY:
            default:
                baseColor = UITheme.PRIMARY;
                hoverColor = UITheme.PRIMARY_HOVER;
                pressedColor = new Color(23, 58, 140);
                break;
        }

        if (!isEnabled()) {
            baseColor = new Color(229, 231, 235);
            g2.setColor(baseColor);
            g2.fillRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
            g2.setColor(UITheme.TEXT_HINT);
        } else {
            Color fill = isPressed ? pressedColor : (isHovered ? hoverColor : baseColor);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);

            if (style == Style.OUTLINE) {
                g2.setColor(UITheme.PRIMARY);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
            }
        }

        // Vẽ chữ
        FontMetrics fm = g2.getFontMetrics(getFont());
        String text = getText();
        int x = (w - fm.stringWidth(text)) / 2;
        int y = (h - fm.getHeight()) / 2 + fm.getAscent();

        g2.setFont(getFont());
        g2.setColor(isEnabled() ? getForeground() : UITheme.TEXT_HINT);
        g2.drawString(text, x, y);

        g2.dispose();
    }
}
