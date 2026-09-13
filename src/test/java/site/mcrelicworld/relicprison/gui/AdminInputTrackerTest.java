package site.mcrelicworld.relicprison.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdminInputTrackerTest {
    @Test
    void twoAdminsConsumeIndependentInputsExactlyOnce() {
        AdminInputTracker<String> tracker = new AdminInputTracker<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        tracker.begin(first, "rank-edit", 2_000L);
        tracker.begin(second, "sell-edit", 2_000L);

        assertEquals("rank-edit", tracker.consume(first, 1_000L).orElseThrow());
        assertFalse(tracker.consume(first, 1_000L).isPresent());
        assertEquals("sell-edit", tracker.consume(second, 1_000L).orElseThrow());
    }

    @Test
    void timeoutAndDisconnectRemovePendingInput() {
        AdminInputTracker<String> tracker = new AdminInputTracker<>();
        UUID player = UUID.randomUUID();
        String input = new String("composition-edit");
        tracker.begin(player, input, 100L);
        assertTrue(tracker.expire(player, input, 101L));
        assertFalse(tracker.pending(player, 101L).isPresent());

        tracker.begin(player, input, 1_000L);
        tracker.clear(player);
        assertFalse(tracker.pending(player, 500L).isPresent());
    }
}
