package site.mcrelicworld.relicprison.message;

import site.mcrelicworld.relicprison.config.FormattingConfig;
import site.mcrelicworld.relicprison.api.NumberFormatService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberFormatter implements NumberFormatService {
    private volatile FormattingConfig config;

    public NumberFormatter(FormattingConfig config) { this.config = config; }
    public void update(FormattingConfig config) { this.config = config; }

    @Override public String full(double value) {
        return decimal(value, config.grouping());
    }

    /** Formats a number with configured decimals but without grouping or a currency symbol. */
    @Override public String plain(double value) {
        return decimal(value, false);
    }

    @Override public String full(BigDecimal value) { return full(value.doubleValue()); }
    @Override public String plain(BigDecimal value) { return plain(value.doubleValue()); }
    @Override public String currency(double value) { return config.currencySymbol() + full(value); }
    @Override public String currency(BigDecimal value) { return config.currencySymbol() + full(value); }
    @Override public String abbreviatedCurrency(double value) { return config.currencySymbol() + abbreviated(value); }
    @Override public String abbreviatedCurrency(BigDecimal value) { return abbreviatedCurrency(value.doubleValue()); }

    @Override public String abbreviated(double value) {
        FormattingConfig c = config;
        double abs = Math.abs(value);
        if (abs < 1000) return full(value);
        int index = Math.min(c.abbreviations().size() - 1, (int) (Math.log(abs) / Math.log(1000)) - 1);
        double scaled = value / Math.pow(1000, index + 1);
        BigDecimal rounded = BigDecimal.valueOf(scaled).setScale(c.abbreviatedDecimals(), RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.toPlainString() + c.abbreviations().get(index);
    }

    private String decimal(double value, boolean grouping) {
        FormattingConfig c = config;
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        String pattern = grouping ? "#,##0" : "0";
        if (c.decimals() > 0) pattern += "." + "0".repeat(c.decimals());
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    @Override public String progress(double fraction) {
        FormattingConfig c = config;
        double safe = Math.max(0, Math.min(1, fraction));
        int filled = (int) Math.round(safe * c.progressLength());
        return c.progressFilled().repeat(filled) + c.progressEmpty().repeat(c.progressLength() - filled);
    }
}
