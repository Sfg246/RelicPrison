package site.mcrelicworld.relicprison.config;

public record GuiSecurityConfig(
        long clickCooldownMillis,
        long sessionTimeoutMillis,
        long confirmationTimeoutMillis
) {
    public GuiSecurityConfig {
        if (clickCooldownMillis < 0L || sessionTimeoutMillis < 1000L || confirmationTimeoutMillis < 1000L) {
            throw new IllegalArgumentException("GUI security durations are outside the supported range");
        }
    }
}
