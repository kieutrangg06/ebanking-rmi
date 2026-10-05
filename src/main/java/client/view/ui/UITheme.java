package client.view.ui;

import java.awt.*;
import java.text.DecimalFormat;

public class UITheme {
    // Primary Palette (Digital Banking Blue & Slate)
    public static final Color PRIMARY = new Color(26, 86, 219);             // #1A56DB Royal Blue
    public static final Color PRIMARY_HOVER = new Color(30, 66, 159);       // #1E429F
    public static final Color PRIMARY_DARK = new Color(15, 23, 42);          // #0F172A Slate 900
    public static final Color PRIMARY_LIGHT = new Color(239, 246, 255);      // #EFF6FF Blue 50

    // Card Gradient (Debit Card)
    public static final Color CARD_GRADIENT_START = new Color(15, 32, 67);  // Deep Navy
    public static final Color CARD_GRADIENT_END = new Color(30, 80, 160);   // Sapphire

    // Accent & States
    public static final Color SUCCESS = new Color(16, 185, 129);            // #10B981 Emerald
    public static final Color SUCCESS_LIGHT = new Color(236, 253, 245);     // #ECFDF5
    public static final Color SUCCESS_DARK = new Color(6, 95, 70);          // #065F46

    public static final Color DANGER = new Color(239, 68, 68);               // #EF4444 Crimson
    public static final Color DANGER_LIGHT = new Color(254, 242, 242);        // #FEF2F2
    public static final Color DANGER_DARK = new Color(153, 27, 27);          // #991B1B

    public static final Color WARNING = new Color(245, 158, 11);            // #F59E0B Amber
    public static final Color WARNING_LIGHT = new Color(254, 252, 232);      // #FEFCE8

    // Backgrounds & Neutrals
    public static final Color BG_MAIN = new Color(243, 244, 246);           // #F3F4F6 Cool Gray 100
    public static final Color BG_CARD = Color.WHITE;
    public static final Color BG_INPUT = new Color(249, 250, 251);          // #F9FAFB Gray 50

    public static final Color BORDER_COLOR = new Color(229, 231, 235);      // #E5E7EB Gray 200
    public static final Color BORDER_FOCUS = new Color(59, 130, 246);       // #3B82F6 Blue 500

    // Typography Colors
    public static final Color TEXT_MAIN = new Color(17, 24, 39);            // #111827 Gray 900
    public static final Color TEXT_MUTED = new Color(107, 114, 128);        // #6B7280 Gray 500
    public static final Color TEXT_HINT = new Color(156, 163, 175);         // #9CA3AF Gray 400

    // Fonts
    public static final Font FONT_TITLE_XL = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font FONT_MONO = new Font("Consolas", Font.BOLD, 14);

    public static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,### VNĐ");

    /**
     * Chuyển số tiền thành chữ tiếng Việt trực quan (chuẩn trải nghiệm ngân hàng)
     */
    public static String toVietnameseCurrencyWords(long number) {
        if (number == 0) return "Không đồng";
        if (number < 0) return "Âm " + toVietnameseCurrencyWords(-number);

        String[] units = {"", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ"};
        String result = "";
        int unitIndex = 0;

        while (number > 0) {
            long block = number % 1000;
            if (block > 0) {
                String blockStr = readThreeDigits((int) block, number >= 1000);
                String unit = units[unitIndex];
                result = blockStr + (unit.isEmpty() ? "" : " " + unit) + (result.isEmpty() ? "" : " " + result);
            }
            number /= 1000;
            unitIndex++;
        }

        result = result.trim();
        if (!result.isEmpty()) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1) + " đồng";
        }
        return result;
    }

    private static String readThreeDigits(int n, boolean hasHigherGroup) {
        String[] digits = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
        int hundreds = n / 100;
        int tens = (n % 100) / 10;
        int ones = n % 10;
        StringBuilder sb = new StringBuilder();

        if (hundreds > 0 || hasHigherGroup) {
            sb.append(digits[hundreds]).append(" trăm");
        }

        if (tens > 1) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(digits[tens]).append(" mươi");
            if (ones == 1) sb.append(" mốt");
            else if (ones == 5) sb.append(" lăm");
            else if (ones > 0) sb.append(" ").append(digits[ones]);
        } else if (tens == 1) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("mười");
            if (ones == 5) sb.append(" lăm");
            else if (ones > 0) sb.append(" ").append(digits[ones]);
        } else if (tens == 0) {
            if (ones > 0) {
                if (sb.length() > 0) sb.append(" linh ");
                sb.append(digits[ones]);
            }
        }

        return sb.toString().trim();
    }
}
