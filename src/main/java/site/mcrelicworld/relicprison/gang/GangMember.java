package site.mcrelicworld.relicprison.gang;

import java.util.UUID;

public record GangMember(
        UUID playerId,
        UUID gangId,
        UUID rankId,
        long joinedAt,
        long lastSeenAt,
        boolean chatEnabled,
        long version
) { }
