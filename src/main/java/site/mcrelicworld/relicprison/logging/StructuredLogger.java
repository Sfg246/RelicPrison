package site.mcrelicworld.relicprison.logging;

import site.mcrelicworld.relicprison.config.LoggingConfig;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StructuredLogger {
    private final Logger logger;
    private volatile LoggingConfig config = LoggingConfig.defaults();

    public StructuredLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void configure(LoggingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void info(LogCategory category, String message) {
        logger.info(format(category, message));
    }

    public void warning(LogCategory category, String message) {
        logger.warning(format(category, message));
    }

    public void severe(LogCategory category, String message) {
        logger.severe(format(category, message));
    }

    public void error(LogCategory category, String message, Throwable error) {
        logger.log(Level.SEVERE, format(category, message), error);
    }

    public void debug(LogCategory category, String message) {
        if (config.debugEnabled(category)) {
            logger.info(format(category, message));
        }
    }

    private static String format(LogCategory category, String message) {
        return "[RelicPrison][" + category.name() + "] " + message;
    }
}
