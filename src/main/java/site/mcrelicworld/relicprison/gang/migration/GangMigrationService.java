package site.mcrelicworld.relicprison.gang.migration;

import site.mcrelicworld.relicprison.gang.GangConfig;
import site.mcrelicworld.relicprison.gang.GangOperationResult;
import site.mcrelicworld.relicprison.gang.GangRank;
import site.mcrelicworld.relicprison.gang.GangRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class GangMigrationService {
    private final GangRepository repository;
    private final GangConfig config;
    private final Map<String, Preview> previews = new ConcurrentHashMap<>();

    public GangMigrationService(GangRepository repository, GangConfig config) {
        this.repository = repository;
        this.config = config;
    }

    public Preview preview(GangMigrationProvider provider) throws Exception {
        if (provider == null || !provider.available()) throw new IllegalArgumentException("Migration provider is unavailable");
        List<GangMigrationProvider.SourceGang> gangs = provider.readPreview();
        List<String> warnings = new ArrayList<>();
        for (GangMigrationProvider.SourceGang gang : gangs) {
            if (gang.ownerId() == null) warnings.add(gang.name() + ": missing owner");
            if (gang.name() == null || gang.name().isBlank()) warnings.add(gang.sourceId() + ": missing name");
        }
        String token = UUID.randomUUID().toString();
        Preview preview = new Preview(token, provider.providerId(), provider.providerVersion(), gangs,
                warnings, System.currentTimeMillis() + 300_000L);
        previews.put(token, preview);
        return preview;
    }

    public CompletableFuture<ImportResult> confirm(String token) {
        Preview preview = previews.remove(token);
        if (preview == null || preview.expiresAt() < System.currentTimeMillis()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Migration preview expired"));
        }
        CompletableFuture<ImportAccumulator> chain = CompletableFuture.completedFuture(new ImportAccumulator());
        for (GangMigrationProvider.SourceGang source : preview.gangs()) {
            chain = chain.thenCompose(accumulator -> importGang(source).handle((result, error) -> {
                if (error == null && result.success()) accumulator.imported++;
                else accumulator.failures.add(source.sourceId() + ": " + (error == null ? result.message() : root(error)));
                return accumulator;
            }));
        }
        return chain.thenApply(value -> new ImportResult(value.imported, List.copyOf(value.failures)));
    }

    private CompletableFuture<GangOperationResult> importGang(GangMigrationProvider.SourceGang source) {
        if (source.ownerId() == null) return CompletableFuture.completedFuture(GangOperationResult.failure("missing owner"));
        long now = System.currentTimeMillis();
        return repository.create(source.ownerId(), source.name(), source.tag(),
                Math.max(config.defaultMemberLimit(), source.members().size()), config.defaultRanks(), now)
                .thenCompose(gang -> repository.ranks(gang.id()).thenCompose(ranks -> {
                    CompletableFuture<GangOperationResult> chain = CompletableFuture.completedFuture(
                            GangOperationResult.success("created"));
                    for (GangMigrationProvider.SourceMember member : source.members()) {
                        if (member.playerId().equals(source.ownerId())) continue;
                        chain = chain.thenCompose(previous -> repository.forceAddMember(gang.id(), member.playerId(),
                                source.ownerId(), now).thenCompose(added -> {
                            if (!added.success()) return CompletableFuture.completedFuture(added);
                            GangRank mapped = closestRank(ranks, member.role());
                            return repository.setMemberRank(gang.id(), member.playerId(), mapped.id(),
                                    source.ownerId(), now);
                        }));
                    }
                    return chain;
                }));
    }

    static GangRank closestRank(List<GangRank> ranks, String sourceRole) {
        String normalized = sourceRole == null ? "member" : sourceRole.toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        String target = switch (normalized) {
            case "admin", "coleader", "co_leader" -> "co_leader";
            case "moderator", "mod", "officer" -> "officer";
            case "veteran" -> "veteran";
            case "recruit" -> "recruit";
            default -> "member";
        };
        return ranks.stream().filter(rank -> rank.systemKey().equals(target)).findFirst()
                .orElseGet(() -> ranks.stream().filter(rank -> rank.systemKey().equals("member"))
                        .findFirst().orElseThrow());
    }

    private static String root(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    public record Preview(String token, String providerId, String providerVersion,
                          List<GangMigrationProvider.SourceGang> gangs, List<String> warnings, long expiresAt) {
        public Preview { gangs = List.copyOf(gangs); warnings = List.copyOf(warnings); }
    }
    public record ImportResult(int imported, List<String> failures) {
        public ImportResult { failures = List.copyOf(failures); }
    }
    private static final class ImportAccumulator {
        private int imported;
        private final List<String> failures = new ArrayList<>();
    }
}
