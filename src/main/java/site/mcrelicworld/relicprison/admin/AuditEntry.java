package site.mcrelicworld.relicprison.admin;

import java.util.UUID;

public record AuditEntry(String auditId, long timestamp, UUID staffId, String staffName, String actionType,
                         String targetType, String targetId, String beforeSummary, String afterSummary,
                         boolean success, String reason, String relatedId, String metadata) { }
