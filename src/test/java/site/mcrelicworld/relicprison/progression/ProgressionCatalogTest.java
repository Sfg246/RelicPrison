package site.mcrelicworld.relicprison.progression;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionCatalogTest {
    @Test
    void rankCostsAndNavigationAreDeterministic() {
        RankServiceImpl service = new RankServiceImpl(null);
        service.apply(List.of(
                rank("a", 0, "10"),
                rank("b", 1, "20"),
                rank("c", 2, "0")
        ));

        assertEquals("b", service.next("a").orElseThrow().id());
        assertEquals("b", service.previous("c").orElseThrow().id());
        assertEquals(new BigDecimal("60"), service.cumulativeCost("a", "c", new BigDecimal("2")));
    }

    @Test
    void prestigeSequenceStartsAtFirstTier() {
        PrestigeServiceImpl service = new PrestigeServiceImpl(null);
        service.apply(List.of(
                prestige("coal", 0, "3.12"),
                prestige("iron", 1, "7.98")
        ));

        assertEquals("coal", service.next(null).orElseThrow().id());
        assertEquals("iron", service.next("coal").orElseThrow().id());
        assertEquals(new BigDecimal("7.98"), service.rankCostMultiplier("iron"));
        assertTrue(service.next("iron").isEmpty());
    }

    private static RankDefinition rank(String id, int order, String cost) {
        return new RankDefinition(id, id.toUpperCase(), order, new BigDecimal(cost), BigDecimal.ONE, id, id, List.of(), List.of());
    }

    private static PrestigeDefinition prestige(String id, int order, String multiplier) {
        return new PrestigeDefinition(id, id, order, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal(multiplier), id, id, List.of(), List.of());
    }
}
