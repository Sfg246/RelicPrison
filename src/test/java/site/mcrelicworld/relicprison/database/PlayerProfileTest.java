package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProfileTest {
    @Test void revisionTrackingDoesNotLoseChangesMadeDuringSave() {
        PlayerProfile profile = PlayerProfile.create(UUID.randomUUID(), "Test", "a", 1L, new PlayerProfile.PeriodKeys("1970-01-01", "1970-W01", "1970-01"));
        long firstRevision = profile.revision();
        profile.markSaved(firstRevision);
        assertFalse(profile.dirty());
        profile.autoSell(true);
        long secondRevision = profile.revision();
        profile.addBlocks(10);
        profile.markSaved(secondRevision);
        assertTrue(profile.dirty(), "A later mutation must remain dirty after an older snapshot is saved");
    }
}
