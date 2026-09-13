package site.mcrelicworld.relicprison.config;

public record PlaceholderConfig(
        long valueCacheMillis,
        long leaderboardCacheMillis,
        long slowThresholdMillis,
        String unavailableText,
        String malformedText
) {
    public PlaceholderConfig {
        if (valueCacheMillis < 50L || leaderboardCacheMillis < 100L || slowThresholdMillis < 1L) {
            throw new IllegalArgumentException("Placeholder cache and slow thresholds must be positive");
        }
        unavailableText = unavailableText == null || unavailableText.isBlank() ? "loading" : unavailableText;
        malformedText = malformedText == null || malformedText.isBlank() ? "invalid" : malformedText;
    }
}
