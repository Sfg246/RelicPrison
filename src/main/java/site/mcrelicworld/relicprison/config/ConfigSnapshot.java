package site.mcrelicworld.relicprison.config;

import java.time.ZoneId;
import java.util.Set;

public record ConfigSnapshot(
        int version,
        ZoneId timezone,
        Set<String> excludedWorlds,
        FeatureToggles features,
        SelectionConfig selection,
        StorageConfig storage,
        FormattingConfig formatting,
        PlaceholderConfig placeholders,
        GuiSecurityConfig guiSecurity,
        LeaderboardConfig leaderboards,
        LoggingConfig logging,
        ResetEngineConfig resetEngine,
        TeleportConfig teleport,
        ProgressionConfig progression,
        WorldGuardConfig worldGuard,
        IntegrationConfig integrations
) {
    public boolean isExcludedWorld(String worldName) {
        return worldName != null && excludedWorlds.contains(worldName.toLowerCase(java.util.Locale.ROOT));
    }

    public ConfigSnapshot withIntegrations(IntegrationConfig nextIntegrations) {
        return new ConfigSnapshot(version, timezone, excludedWorlds, features.withIntegrations(nextIntegrations),
                selection, storage, formatting, placeholders, guiSecurity, leaderboards, logging, resetEngine, teleport, progression,
                nextIntegrations.worldGuard(), nextIntegrations);
    }

    public ConfigSnapshot withLogging(LoggingConfig nextLogging) {
        return new ConfigSnapshot(version, timezone, excludedWorlds, features, selection, storage, formatting,
                placeholders, guiSecurity, leaderboards, nextLogging, resetEngine, teleport, progression, worldGuard,
                integrations);
    }
}
