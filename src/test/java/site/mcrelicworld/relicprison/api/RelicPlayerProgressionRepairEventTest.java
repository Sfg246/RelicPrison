package site.mcrelicworld.relicprison.api;

import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.api.event.RelicPlayerProgressionRepairEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RelicPlayerProgressionRepairEventTest {
    @Test
    void repairEventDefensivelyCopiesMutableInputs() {
        Set<String> rankGroups = new HashSet<>(Set.of("rank_a"));
        Set<String> prestigeGroups = new HashSet<>(Set.of("prestige_coal"));
        List<String> repairs = new ArrayList<>(List.of("removed duplicate rank"));
        RelicPlayerProgressionRepairEvent event = new RelicPlayerProgressionRepairEvent(UUID.randomUUID(), "Andre",
                rankGroups, prestigeGroups, "a", "coal", repairs, "join", true);

        rankGroups.add("rank_b");
        prestigeGroups.clear();
        repairs.add("late mutation");

        assertEquals(Set.of("rank_a"), event.previousDirectRankGroups());
        assertEquals(Set.of("prestige_coal"), event.previousDirectPrestigeGroups());
        assertEquals(List.of("removed duplicate rank"), event.repairsPerformed());
        assertThrows(UnsupportedOperationException.class, () -> event.repairsPerformed().add("blocked"));
    }
}
