package site.mcrelicworld.relicprison.config;

import site.mcrelicworld.relicprison.logging.LogCategory;

import java.util.EnumMap;
import java.util.Map;

public record LoggingConfig(boolean debug, Map<LogCategory, Boolean> categories) {
    public LoggingConfig {
        EnumMap<LogCategory, Boolean> copy = new EnumMap<>(LogCategory.class);
        for (LogCategory category : LogCategory.values()) {
            copy.put(category, Boolean.TRUE.equals(categories.get(category)));
        }
        categories = Map.copyOf(copy);
    }

    public boolean debugEnabled(LogCategory category) {
        return debug || Boolean.TRUE.equals(categories.get(category));
    }

    public static LoggingConfig defaults() {
        EnumMap<LogCategory, Boolean> categories = new EnumMap<>(LogCategory.class);
        for (LogCategory category : LogCategory.values()) {
            categories.put(category, false);
        }
        return new LoggingConfig(false, categories);
    }
}
