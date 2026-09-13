package site.mcrelicworld.relicprison.api;

import java.math.BigDecimal;

public interface NumberFormatService {
    /** Thread-safe. Formats with grouping and configured decimals. */
    String full(double value);
    /** Thread-safe. Formats without grouping or currency symbol. */
    String plain(double value);
    /** Thread-safe. Formats with grouping and configured decimals. */
    String full(BigDecimal value);
    /** Thread-safe. Formats without grouping or currency symbol. */
    String plain(BigDecimal value);
    /** Thread-safe. Formats with the configured currency symbol. */
    String currency(double value);
    /** Thread-safe. Formats with the configured currency symbol. */
    String currency(BigDecimal value);
    /** Thread-safe. Formats an abbreviated value with currency symbol. */
    String abbreviatedCurrency(double value);
    /** Thread-safe. Formats an abbreviated value with currency symbol. */
    String abbreviatedCurrency(BigDecimal value);
    /** Thread-safe. Formats an abbreviated value without currency symbol. */
    String abbreviated(double value);
    /** Thread-safe. Formats a bounded progress bar for the supplied fraction. */
    String progress(double fraction);
}
