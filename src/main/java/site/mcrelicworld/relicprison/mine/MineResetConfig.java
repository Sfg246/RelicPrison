package site.mcrelicworld.relicprison.mine;

import java.util.List;
import java.util.Objects;

public record MineResetConfig(
        boolean timedEnabled,
        int intervalSeconds,
        boolean percentageEnabled,
        double minedPercentage,
        List<Integer> warningSeconds,
        int countdownSeconds,
        NotificationScope notificationScope,
        int notificationRadius,
        boolean evacuatePlayers,
        ResetOrder order,
        List<String> beforeCommands,
        List<String> startCommands,
        List<String> completeCommands,
        List<String> failedCommands,
        boolean enabled,
        int retryCount,
        int retryDelaySeconds,
        RecountBehavior recountBehavior,
        TeleportDestination teleportDestination,
        boolean requireOutsideDestination
) {
    public enum NotificationScope { MINE, RADIUS, GLOBAL, NONE }
    public enum ResetOrder {
        CHUNK_GROUPED_BOTTOM_UP,
        CHUNK_GROUPED_TOP_DOWN,
        BOTTOM_TO_TOP,
        TOP_TO_BOTTOM,
        LAYERED,
        CENTER_OUTWARD,
        OUTSIDE_INWARD,
        DETERMINISTIC_SHUFFLED
    }
    public enum RecountBehavior { NEVER, AFTER_FAILURE }
    public enum TeleportDestination { MINE_SPAWN, WORLD_SPAWN }

    public MineResetConfig(boolean timedEnabled, int intervalSeconds, boolean percentageEnabled,
                           double minedPercentage, List<Integer> warningSeconds,
                           NotificationScope notificationScope, int notificationRadius,
                           boolean evacuatePlayers, ResetOrder order, List<String> beforeCommands,
                           List<String> startCommands, List<String> completeCommands,
                           List<String> failedCommands) {
        this(timedEnabled, intervalSeconds, percentageEnabled, minedPercentage, warningSeconds,
                warningSeconds.stream().mapToInt(Integer::intValue).max().orElse(0), notificationScope,
                notificationRadius, evacuatePlayers, order, beforeCommands,
                startCommands, completeCommands, failedCommands, true, 3, 60,
                RecountBehavior.AFTER_FAILURE, TeleportDestination.MINE_SPAWN, true);
    }

    public MineResetConfig(boolean timedEnabled, int intervalSeconds, boolean percentageEnabled,
                           double minedPercentage, List<Integer> warningSeconds,
                           NotificationScope notificationScope, int notificationRadius,
                           boolean evacuatePlayers, ResetOrder order, List<String> beforeCommands,
                           List<String> startCommands, List<String> completeCommands,
                           List<String> failedCommands, boolean enabled, int retryCount,
                           int retryDelaySeconds, RecountBehavior recountBehavior,
                           TeleportDestination teleportDestination, boolean requireOutsideDestination) {
        this(timedEnabled, intervalSeconds, percentageEnabled, minedPercentage, warningSeconds,
                warningSeconds.stream().mapToInt(Integer::intValue).max().orElse(0), notificationScope,
                notificationRadius, evacuatePlayers, order, beforeCommands, startCommands, completeCommands,
                failedCommands, enabled, retryCount, retryDelaySeconds, recountBehavior, teleportDestination,
                requireOutsideDestination);
    }

    public MineResetConfig {
        if (intervalSeconds <= 0) throw new IllegalArgumentException("Reset interval must be positive");
        if (!Double.isFinite(minedPercentage) || minedPercentage < 0 || minedPercentage > 100) {
            throw new IllegalArgumentException("Reset percentage must be between 0 and 100");
        }
        warningSeconds = List.copyOf(Objects.requireNonNull(warningSeconds));
        if (countdownSeconds < 0) throw new IllegalArgumentException("Reset countdown cannot be negative");
        if (warningSeconds.stream().anyMatch(warning -> warning == null || warning < 0 || warning > countdownSeconds)) {
            throw new IllegalArgumentException("Reset warnings must be within the countdown");
        }
        Objects.requireNonNull(notificationScope);
        if (notificationRadius < 0) throw new IllegalArgumentException("Notification radius cannot be negative");
        Objects.requireNonNull(order);
        beforeCommands = List.copyOf(Objects.requireNonNull(beforeCommands));
        startCommands = List.copyOf(Objects.requireNonNull(startCommands));
        completeCommands = List.copyOf(Objects.requireNonNull(completeCommands));
        failedCommands = List.copyOf(Objects.requireNonNull(failedCommands));
        if (retryCount < 0 || retryCount > 100) throw new IllegalArgumentException("Reset retry count must be 0-100");
        if (retryDelaySeconds <= 0) throw new IllegalArgumentException("Reset retry delay must be positive");
        Objects.requireNonNull(recountBehavior);
        Objects.requireNonNull(teleportDestination);
    }

    public static MineResetConfig defaults(int intervalSeconds, double percentage, List<Integer> warnings) {
        return new MineResetConfig(true, intervalSeconds, true, percentage, warnings,
                warnings.stream().mapToInt(Integer::intValue).max().orElse(0), NotificationScope.MINE, 64,
                true, ResetOrder.CHUNK_GROUPED_BOTTOM_UP,
                List.of(), List.of(), List.of(), List.of(), true, 3, 60,
                RecountBehavior.AFTER_FAILURE, TeleportDestination.MINE_SPAWN, true);
    }
}
