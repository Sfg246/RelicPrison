package site.mcrelicworld.relicprison.gang;

import java.util.UUID;

public record GangAuditEntry(
        UUID id,
        UUID gangId,
        UUID actorId,
        String actionType,
        String targetType,
        String targetId,
        String beforeSummary,
        String afterSummary,
        boolean success,
        String reason,
        String metadata,
        long createdAt
) { }
