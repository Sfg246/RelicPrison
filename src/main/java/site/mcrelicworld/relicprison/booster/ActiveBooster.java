package site.mcrelicworld.relicprison.booster;

import site.mcrelicworld.relicprison.api.model.BoosterView;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ActiveBooster(
        String id,
        UUID owner,
        BigDecimal multiplier,
        long expiresAt,
        long createdAt,
        boolean serverWide,
        String activatedBy,
        long startsAt,
        boolean enabled
) {
    public ActiveBooster(String id, UUID owner, BigDecimal multiplier, long expiresAt, long createdAt,
                         boolean serverWide, String activatedBy) {
        this(id, owner, multiplier, expiresAt, createdAt, serverWide, activatedBy, createdAt, true);
    }

    public ActiveBooster {
        Objects.requireNonNull(id);
        Objects.requireNonNull(multiplier);
        if (multiplier.signum() <= 0) throw new IllegalArgumentException("Booster multiplier must be positive");
        if (!serverWide && owner == null) throw new IllegalArgumentException("Personal booster requires an owner");
    }
    public boolean expired(long now) { return expiresAt <= now; }
    public boolean scheduled(long now) { return enabled && startsAt > now; }
    public boolean active(long now) { return enabled && startsAt <= now && !expired(now); }
    public BoosterView view() { return new BoosterView(id, owner, multiplier, expiresAt, serverWide); }
}
