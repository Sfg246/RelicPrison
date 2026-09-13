package site.mcrelicworld.relicprison.mine;

import site.mcrelicworld.relicprison.api.model.BlockPosition;
import site.mcrelicworld.relicprison.api.model.MineView;
import site.mcrelicworld.relicprison.mine.composition.MineComposition;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MineDefinition(
        String id,
        String displayName,
        UUID worldId,
        String worldName,
        Cuboid bounds,
        MineSpawn spawn,
        boolean enabled,
        int sortOrder,
        String requiredRank,
        String requiredPrestige,
        String accessPermission,
        Map<String, String> metadata,
        MineComposition composition,
        MineResetConfig resetConfig
) implements MineView {
    public MineDefinition {
        id = normalizeId(id);
        displayName = Objects.requireNonNullElse(displayName, id);
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) throw new IllegalArgumentException("worldName cannot be blank");
        Objects.requireNonNull(bounds, "bounds");
        requiredRank = normalizeOptional(requiredRank);
        requiredPrestige = normalizeOptional(requiredPrestige);
        accessPermission = normalizePermission(accessPermission);
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        composition = Objects.requireNonNullElseGet(composition, MineComposition::defaultStone);
        resetConfig = Objects.requireNonNull(resetConfig, "resetConfig");
    }

    public static String normalizeId(String input) {
        if (input == null || !input.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Mine ID must be 1-32 characters using letters, numbers, _ or -");
        }
        return input.toLowerCase(java.util.Locale.ROOT);
    }

    @Override public BlockPosition minimum() { return bounds.minimum(); }
    @Override public BlockPosition maximum() { return bounds.maximum(); }
    @Override public Optional<BlockPosition> spawnBlock() { return spawn == null ? Optional.empty() : Optional.of(spawn.blockPosition()); }
    @Override public Map<String, Double> compositionWeights() { return composition.weights(); }
    @Override public long volume() { return bounds.volume(); }

    public MineDefinition withIdentity(String newId, String newDisplayName) {
        return new MineDefinition(newId, newDisplayName, worldId, worldName, bounds, spawn, enabled, sortOrder,
                requiredRank, requiredPrestige, accessPermission, metadata, composition, resetConfig);
    }

    public MineDefinition withSortOrder(int value) {
        return new MineDefinition(id, displayName, worldId, worldName, bounds, spawn, enabled, value,
                requiredRank, requiredPrestige, accessPermission, metadata, composition, resetConfig);
    }

    public MineDefinition withBounds(UUID newWorldId, String newWorldName, Cuboid newBounds) {
        return new MineDefinition(id, displayName, newWorldId, newWorldName, newBounds, spawn, enabled, sortOrder,
                requiredRank, requiredPrestige, accessPermission, metadata, composition, resetConfig);
    }

    public MineDefinition withSpawn(MineSpawn newSpawn) {
        return new MineDefinition(id, displayName, worldId, worldName, bounds, newSpawn, enabled, sortOrder,
                requiredRank, requiredPrestige, accessPermission, metadata, composition, resetConfig);
    }

    public MineDefinition withEnabled(boolean value) {
        return new MineDefinition(id, displayName, worldId, worldName, bounds, spawn, value, sortOrder,
                requiredRank, requiredPrestige, accessPermission, metadata, composition, resetConfig);
    }

    public MineDefinition withComposition(MineComposition value) {
        return new MineDefinition(id, displayName, worldId, worldName, bounds, spawn, enabled, sortOrder,
                requiredRank, requiredPrestige, accessPermission, metadata, value, resetConfig);
    }

    public MineDefinition withMetadata(Map<String, String> value) {
        return new MineDefinition(id, displayName, worldId, worldName, bounds, spawn, enabled, sortOrder,
                requiredRank, requiredPrestige, accessPermission, value, composition, resetConfig);
    }

    public MineDefinition withRequirements(String rank, String prestige) {
        return new MineDefinition(id, displayName, worldId, worldName, bounds, spawn, enabled, sortOrder,
                normalizeOptional(rank), normalizeOptional(prestige), accessPermission, metadata, composition, resetConfig);
    }

    public MineDefinition withAccessPermission(String permission) {
        return new MineDefinition(id, displayName, worldId, worldName, bounds, spawn, enabled, sortOrder,
                requiredRank, requiredPrestige, normalizePermission(permission), metadata, composition, resetConfig);
    }

    public MineDefinition withResetConfig(MineResetConfig value) {
        return new MineDefinition(id, displayName, worldId, worldName, bounds, spawn, enabled, sortOrder,
                requiredRank, requiredPrestige, accessPermission, metadata, composition, value);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizePermission(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]{1,128}")) {
            throw new IllegalArgumentException("Mine access permission contains invalid characters");
        }
        return normalized;
    }
}
