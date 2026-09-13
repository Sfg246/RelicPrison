package site.mcrelicworld.relicprison.mine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class MineSpatialIndex {
    private final Map<UUID, Map<Long, List<MineDefinition>>> byWorldAndChunk;

    private MineSpatialIndex(Map<UUID, Map<Long, List<MineDefinition>>> byWorldAndChunk) {
        this.byWorldAndChunk = byWorldAndChunk;
    }

    static MineSpatialIndex build(Collection<MineDefinition> mines) {
        Map<UUID, Map<Long, List<MineDefinition>>> result = new HashMap<>();
        for (MineDefinition mine : mines) {
            int minChunkX = mine.bounds().minimum().x() >> 4;
            int maxChunkX = mine.bounds().maximum().x() >> 4;
            int minChunkZ = mine.bounds().minimum().z() >> 4;
            int maxChunkZ = mine.bounds().maximum().z() >> 4;
            Map<Long, List<MineDefinition>> chunks = result.computeIfAbsent(mine.worldId(), ignored -> new HashMap<>());
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.computeIfAbsent(pack(chunkX, chunkZ), ignored -> new ArrayList<>()).add(mine);
                }
            }
        }
        result.values().forEach(chunks -> chunks.replaceAll((ignored, list) -> list.stream()
                .sorted(Comparator.comparingLong(MineDefinition::volume)).toList()));
        return new MineSpatialIndex(Map.copyOf(result));
    }

    Optional<MineDefinition> find(UUID worldId, int x, int y, int z) {
        Map<Long, List<MineDefinition>> chunks = byWorldAndChunk.get(worldId);
        if (chunks == null) return Optional.empty();
        List<MineDefinition> candidates = chunks.get(pack(x >> 4, z >> 4));
        if (candidates == null) return Optional.empty();
        for (MineDefinition mine : candidates) {
            if (mine.enabled() && mine.bounds().contains(x, y, z)) return Optional.of(mine);
        }
        return Optional.empty();
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
