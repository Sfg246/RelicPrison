package site.mcrelicworld.relicprison.config;

import java.util.List;

public record FormattingConfig(
        String currencySymbol,
        int decimals,
        int abbreviatedDecimals,
        boolean grouping,
        List<String> abbreviations,
        int progressLength,
        String progressFilled,
        String progressEmpty
) {}
