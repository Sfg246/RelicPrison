package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.util.UUID;

public record Gang(
        UUID id,
        String name,
        String tag,
        String description,
        UUID ownerId,
        long createdAt,
        int level,
        BigDecimal xp,
        long points,
        BigDecimal bankBalance,
        int memberLimit,
        int memberCount,
        GangJoinMode joinMode,
        String color,
        String motd,
        GangHome home,
        boolean enabled,
        long version,
        long updatedAt
) {
    public Gang {
        xp = xp == null ? BigDecimal.ZERO : xp;
        bankBalance = bankBalance == null ? BigDecimal.ZERO : bankBalance;
    }

    public record GangHome(String world, double x, double y, double z, float yaw, float pitch) { }
}
