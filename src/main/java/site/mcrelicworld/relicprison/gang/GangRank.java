package site.mcrelicworld.relicprison.gang;

import java.util.Set;
import java.util.UUID;

public record GangRank(
        UUID id,
        UUID gangId,
        String systemKey,
        String displayName,
        int priority,
        String color,
        boolean defaultRank,
        Set<GangPermission> permissions,
        long createdAt,
        long updatedAt
) {
    public GangRank {
        systemKey = systemKey == null ? "" : systemKey;
        permissions = Set.copyOf(permissions);
    }

    public boolean owner() {
        return systemKey.equals("owner");
    }
}
