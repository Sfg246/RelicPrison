package site.mcrelicworld.relicprison.config;

import org.bukkit.Sound;

import java.util.List;
import java.util.Map;

public record ResetEngineConfig(
        double targetTimePerTickMillis,
        int initialBlocksPerTick,
        int minimumBlocksPerTick,
        int maximumBlocksPerTick,
        double itemsAdderTargetTimePerTickMillis,
        int itemsAdderInitialBlocksPerTick,
        int itemsAdderMinimumBlocksPerTick,
        int itemsAdderMaximumBlocksPerTick,
        int maximumConcurrentMines,
        Map<String, Integer> perWorldConcurrentLimits,
        boolean emptyMineTriggerEnabled,
        double pauseAboveMspt,
        double resumeBelowMspt,
        int defaultIntervalSeconds,
        double defaultMinedPercentage,
        List<Integer> defaultWarnings,
        int runtimeSaveIntervalSeconds,
        int failureRetryDelaySeconds,
        boolean chatNotifications,
        boolean titleNotifications,
        boolean actionBarNotifications,
        boolean soundNotifications,
        Sound warningSound,
        Sound completionSound,
        Sound failureSound
) {
    public ResetEngineConfig {
        defaultWarnings = List.copyOf(defaultWarnings);
        perWorldConcurrentLimits = Map.copyOf(perWorldConcurrentLimits);
    }
}
