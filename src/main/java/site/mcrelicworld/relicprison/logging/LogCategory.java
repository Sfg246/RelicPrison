package site.mcrelicworld.relicprison.logging;

import java.util.Locale;

public enum LogCategory {
    STARTUP,
    CONFIG,
    DATABASE,
    MINES,
    RESET,
    PROGRESSION,
    ECONOMY,
    SELLING,
    MINING,
    INTEGRATION,
    MIGRATION,
    BACKUP,
    DIAGNOSTIC;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
