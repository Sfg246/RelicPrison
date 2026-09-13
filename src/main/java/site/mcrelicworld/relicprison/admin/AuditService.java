package site.mcrelicworld.relicprison.admin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AuditService {
    private static final int MAX_FIELD_LENGTH = 2048;

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Void> record(CommandSender actor, String actionType, String targetType, String targetId,
                                          String beforeSummary, String afterSummary, boolean success, String reason,
                                          String relatedId, String metadata) {
        UUID staffId = actor instanceof Player player ? player.getUniqueId() : null;
        String staffName = actor == null ? "system" : actor.getName();
        return record(staffId, staffName, actionType, targetType, targetId, beforeSummary, afterSummary, success,
                reason, relatedId, metadata);
    }

    public CompletableFuture<Void> record(UUID staffId, String staffName, String actionType, String targetType,
                                          String targetId, String beforeSummary, String afterSummary, boolean success,
                                          String reason, String relatedId, String metadata) {
        AuditEntry entry = new AuditEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), staffId,
                safeText(staffName, 64), normalize(actionType), normalize(targetType),
                safeText(targetId, 128), safeText(beforeSummary, MAX_FIELD_LENGTH),
                safeText(afterSummary, MAX_FIELD_LENGTH), success, safeText(reason, 512),
                safeText(relatedId, 128), safeText(metadata, MAX_FIELD_LENGTH));
        return repository.record(entry);
    }

    public CompletableFuture<List<AuditEntry>> query(AuditQuery query) {
        return repository.query(query);
    }

    public AuditQuery parseQuery(String[] args, int startIndex) {
        int page = 1;
        Optional<UUID> staff = Optional.empty();
        Optional<String> action = Optional.empty();
        Optional<String> targetType = Optional.empty();
        Optional<String> targetId = Optional.empty();
        Optional<Long> from = Optional.empty();
        Optional<Long> to = Optional.empty();
        for (int index = startIndex; index < args.length; index++) {
            String raw = args[index];
            if (raw.matches("\\d+")) {
                page = Integer.parseInt(raw);
                continue;
            }
            String[] pair = raw.split("=", 2);
            if (pair.length != 2) continue;
            String key = pair[0].toLowerCase(Locale.ROOT);
            String value = pair[1];
            switch (key) {
                case "staff" -> {
                    try { staff = Optional.of(UUID.fromString(value)); }
                    catch (IllegalArgumentException ignored) { }
                }
                case "action" -> action = Optional.of(value);
                case "target" -> {
                    String[] target = value.split(":", 2);
                    targetType = Optional.of(target[0]);
                    if (target.length > 1) targetId = Optional.of(target[1]);
                }
                case "from" -> from = parseDate(value, true);
                case "to" -> to = parseDate(value, false);
                default -> { }
            }
        }
        return new AuditQuery(page, 20, staff, action, targetType, targetId, from, to);
    }

    private static Optional<Long> parseDate(String value, boolean startOfDay) {
        try {
            LocalDate date = LocalDate.parse(value);
            Instant instant = startOfDay ? date.atStartOfDay(ZoneOffset.UTC).toInstant()
                    : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
            return Optional.of(instant.toEpochMilli());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String normalize(String value) {
        return safeText(value, 64).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String safeText(String value, int limit) {
        String redacted = RedactionService.redactInline(value == null ? "" : value)
                .replace('\n', ' ').replace('\r', ' ');
        if (redacted.length() <= limit) return redacted;
        return redacted.substring(0, limit);
    }
}
