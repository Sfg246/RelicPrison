package site.mcrelicworld.relicprison.admin;

import java.util.Optional;
import java.util.UUID;

public record AuditQuery(int page, int pageSize, Optional<UUID> staffId, Optional<String> actionType,
                         Optional<String> targetType, Optional<String> targetId, Optional<Long> from,
                         Optional<Long> to) {
    public AuditQuery {
        page = Math.max(1, page);
        pageSize = Math.max(1, Math.min(100, pageSize));
        staffId = staffId == null ? Optional.empty() : staffId;
        actionType = normalize(actionType);
        targetType = normalize(targetType);
        targetId = normalize(targetId);
        from = from == null ? Optional.empty() : from;
        to = to == null ? Optional.empty() : to;
    }

    private static Optional<String> normalize(Optional<String> value) {
        if (value == null || value.isEmpty() || value.get().isBlank()) return Optional.empty();
        return Optional.of(value.get().trim().toLowerCase(java.util.Locale.ROOT));
    }
}
