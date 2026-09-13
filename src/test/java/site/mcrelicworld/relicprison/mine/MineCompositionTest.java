package site.mcrelicworld.relicprison.mine;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.mine.composition.BlockTypeRef;
import site.mcrelicworld.relicprison.mine.composition.CompositionEntry;
import site.mcrelicworld.relicprison.mine.composition.MineComposition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineCompositionTest {
    @Test void rejectsInvalidWeightsAndEmptyCompositions() {
        assertThrows(IllegalArgumentException.class, () -> new MineComposition(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CompositionEntry(BlockTypeRef.parse("STONE"), 0));
    }

    @Test void normalizesToOneHundredPercent() {
        MineComposition composition = new MineComposition(List.of(
                new CompositionEntry(BlockTypeRef.parse("STONE"), 7),
                new CompositionEntry(BlockTypeRef.parse("COAL_ORE"), 3)
        )).normalized();
        assertEquals(100.0, composition.totalWeight(), 0.000001);
        assertEquals(70.0, composition.weights().get("vanilla:stone"), 0.000001);
        assertEquals(30.0, composition.weights().get("vanilla:coal_ore"), 0.000001);
    }

    @Test void compiledSamplerOnlyReturnsConfiguredBlocks() {
        MineComposition composition = new MineComposition(List.of(
                new CompositionEntry(BlockTypeRef.parse("STONE"), 50),
                new CompositionEntry(BlockTypeRef.parse("DIAMOND_ORE"), 50)
        ));
        for (int i = 0; i < 1000; i++) {
            String id = composition.compiled().sample().id();
            assertTrue(id.equals("STONE") || id.equals("DIAMOND_ORE"));
        }
    }

    @Test void supportsProviderQualifiedEntriesAndExplicitAir() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompositionEntry(BlockTypeRef.parse("air"), 1));
        MineComposition composition = new MineComposition(List.of(
                new CompositionEntry(BlockTypeRef.parse("vanilla:stone"), 80),
                new CompositionEntry(BlockTypeRef.parse("itemsadder:custom:ore"), 10),
                new CompositionEntry(BlockTypeRef.parse("air"), 10, null, java.util.Map.of(), true)
        ));
        assertTrue(composition.weights().containsKey("vanilla:stone"));
        assertTrue(composition.weights().containsKey("itemsadder:custom:ore"));
        assertTrue(composition.weights().containsKey("vanilla:air"));
    }

    @Test void weightedSamplerDistributionIsStatisticallyReasonable() {
        MineComposition composition = new MineComposition(List.of(
                new CompositionEntry(BlockTypeRef.parse("STONE"), 80),
                new CompositionEntry(BlockTypeRef.parse("DIAMOND_ORE"), 20)
        ));
        int stone = 0;
        int samples = 100_000;
        for (int i = 0; i < samples; i++) {
            if (composition.compiled().sample().qualifiedId().equals("vanilla:stone")) stone++;
        }
        double ratio = stone / (double) samples;
        assertTrue(ratio > 0.775 && ratio < 0.825, "stone ratio was " + ratio);
    }

    @Test void validatesAndExposesRuntimeProviderFallbackMetadata() {
        CompositionEntry entry = new CompositionEntry(BlockTypeRef.parse("itemsadder:namespace:block"), 1.0,
                null, Map.of("fallback-material", "STONE"), false);
        assertEquals(Material.STONE, entry.fallbackMaterial().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new CompositionEntry(
                BlockTypeRef.parse("itemsadder:namespace:block"), 1.0, null, Map.of("ignored", "value"), false));
    }
}
