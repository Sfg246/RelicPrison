package site.mcrelicworld.relicprison.message;

import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.config.FormattingConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberFormatterTest {
    private final NumberFormatter formatter = new NumberFormatter(new FormattingConfig(
            "$", 2, 2, true, List.of("K", "M", "B", "T"), 10, "█", "░"));

    @Test void formatsCurrencyAbbreviationsAndProgress() {
        assertEquals("$1,250.00", formatter.currency(1250));
        assertEquals("1250.00", formatter.plain(1250));
        assertEquals("1.25M", formatter.abbreviated(1_250_000));
        assertEquals("$1.25M", formatter.abbreviatedCurrency(1_250_000));
        assertEquals("███████░░░", formatter.progress(0.7));
    }
}
