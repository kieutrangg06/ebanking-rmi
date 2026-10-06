package client;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationAndCsvTest {

    private final String dateRegex = "^\\d{4}-\\d{2}-\\d{2}$";

    @Test
    public void testDateRegexValidation() {
        assertTrue("2026-09-01".matches(dateRegex));
        assertTrue("2026-12-31".matches(dateRegex));
        assertFalse("2026/09/01".matches(dateRegex));
        assertFalse("01-09-2026".matches(dateRegex));
        assertFalse("2026-9-1".matches(dateRegex));
        assertFalse("abcd-ef-gh".matches(dateRegex));
        assertFalse("".matches(dateRegex));
    }

    @Test
    public void testStrictDateFormatParsing() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setLenient(false);

        assertDoesNotThrow(() -> {
            Date d = sdf.parse("2026-09-19");
            assertNotNull(d);
        });

        // Ngày không tồn tại trong tháng
        assertThrows(ParseException.class, () -> sdf.parse("2026-02-30"));
        assertThrows(ParseException.class, () -> sdf.parse("2026-13-01"));
        assertThrows(ParseException.class, () -> sdf.parse("2026-00-10"));
    }

    @Test
    public void testCsvEscaping() {
        assertEquals("Normal text", escapeCsv("Normal text"));
        assertEquals("\"Text, with comma\"", escapeCsv("Text, with comma"));
        assertEquals("\"Text with \"\"quotes\"\"\"", escapeCsv("Text with \"quotes\""));
        assertEquals("\"Line1\nLine2\"", escapeCsv("Line1\nLine2"));
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        return value;
    }
}
