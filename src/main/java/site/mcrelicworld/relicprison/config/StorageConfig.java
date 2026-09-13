package site.mcrelicworld.relicprison.config;

import java.nio.file.Path;

public record StorageConfig(
        Type type,
        Path sqliteFile,
        String host,
        int port,
        String database,
        String username,
        String password,
        String parameters,
        int poolSize,
        int queueCapacity,
        int retryCount,
        int retryDelayMillis,
        int profileLoadTimeoutSeconds,
        int saveIntervalSeconds,
        int shutdownFlushTimeoutSeconds
) {
    public enum Type { SQLITE, MYSQL }
}
