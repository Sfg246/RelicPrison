package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.util.UUID;

public record GangBooster(
        UUID id,
        UUID gangId,
        Type type,
        BigDecimal multiplier,
        long startsAt,
        long expiresAt,
        UUID activatedBy,
        boolean enabled,
        String source,
        long createdAt
) {
    public enum Type { SELL, MINING_XP, GANG_XP }

    public boolean active(long now) {
        return enabled && startsAt <= now && expiresAt > now;
    }
}
