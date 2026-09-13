package site.mcrelicworld.relicprison.mining;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BulkMiningCommittedResultTest {
    @Test
    void frozenCommittedResultRoundTripsEveryRecoveryField() {
        BulkMiningCommittedResult original = new BulkMiningCommittedResult(123L, 4L, 9L,
                new BigDecimal("42.75"), 31, false,
                List.of(new BulkMiningCommittedResult.Period("daily", "2026-09-10"),
                        new BulkMiningCommittedResult.Period("lifetime", "lifetime")),
                List.of(new BulkMiningCommittedResult.MineResult("a", Map.of("STONE", 3, "COAL_ORE", 1),
                        Map.of("relic:ore", 1), 2, 1, 1, 5)));

        assertEquals(original, BulkMiningCommittedResult.decode(original.encode()));
    }

    @Test
    void corruptPayloadCannotBeRecoveredAsAChangedPlan() {
        assertThrows(IllegalArgumentException.class, () -> BulkMiningCommittedResult.decode("not-a-payload"));
    }
}
