package site.mcrelicworld.relicprison.api.model;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface MineView {
    String id();
    String displayName();
    UUID worldId();
    String worldName();
    BlockPosition minimum();
    BlockPosition maximum();
    Optional<BlockPosition> spawnBlock();
    boolean enabled();
    int sortOrder();
    String requiredRank();
    String requiredPrestige();
    String accessPermission();
    Map<String, String> metadata();
    Map<String, Double> compositionWeights();
    long volume();
}
