package site.mcrelicworld.relicprison.config;

import org.bukkit.Material;

public record SelectionConfig(
        int timeoutSeconds,
        int previewRefreshTicks,
        int previewMaxParticles,
        int previewSpacing,
        long maximumVolume,
        boolean allowOverlap,
        Material wandMaterial
) {}
