package site.mcrelicworld.relicprison.mine.composition;

import org.bukkit.Material;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record CompositionEntry(BlockTypeRef block, double weight, String minimumPrestige,
                               Map<String, String> properties, boolean explicitAir) {
    public CompositionEntry(BlockTypeRef block, double weight) {
        this(block, weight, null, Map.of(), false);
    }

    public CompositionEntry {
        Objects.requireNonNull(block, "block");
        if (!Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Weight must be positive");
        minimumPrestige = minimumPrestige == null || minimumPrestige.isBlank()
                ? null : minimumPrestige.trim().toLowerCase(java.util.Locale.ROOT);
        properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
        if (block.airBlock() && !explicitAir) {
            throw new IllegalArgumentException("Air composition entries require explicit allow-air=true");
        }
        if (block.kind() == BlockTypeRef.Kind.VANILLA && !properties.isEmpty()) {
            throw new IllegalArgumentException("Vanilla composition entries do not support provider metadata");
        }
        Set<String> unsupported = properties.keySet().stream()
                .filter(key -> !key.equalsIgnoreCase("fallback-material")).collect(java.util.stream.Collectors.toSet());
        if (!unsupported.isEmpty()) throw new IllegalArgumentException("Unsupported provider metadata: " + unsupported);
        fallbackMaterial(properties);
    }

    public Optional<Material> fallbackMaterial() {
        return fallbackMaterial(properties);
    }

    private static Optional<Material> fallbackMaterial(Map<String, String> properties) {
        String configured = properties.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("fallback-material"))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        if (configured == null || configured.isBlank()) return Optional.empty();
        Material material = Material.matchMaterial(configured);
        if (material == null || !material.isBlock() || material.isAir()) {
            throw new IllegalArgumentException("fallback-material must be a non-air vanilla block");
        }
        return Optional.of(material);
    }
}
