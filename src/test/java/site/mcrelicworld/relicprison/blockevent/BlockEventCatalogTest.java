package site.mcrelicworld.relicprison.blockevent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BlockEventCatalogTest {
    @TempDir Path temp;

    @Test
    void disabledEventsAreNotLoaded() throws Exception {
        Files.writeString(temp.resolve("block-events.yml"), """
                events:
                  disabled:
                    enabled: false
                    trigger: chance-per-action
                  enabled:
                    enabled: true
                    trigger: every-x-blocks
                    every-x-blocks: 100
                    mining-type: bulk
                """);

        BlockEventCatalog catalog = BlockEventCatalog.load(temp.toFile());

        assertEquals(1, catalog.events().size());
        assertEquals("enabled", catalog.events().getFirst().id());
        assertEquals(BlockEventCatalog.Trigger.EVERY_X_BLOCKS, catalog.events().getFirst().trigger());
        assertEquals(BlockEventCatalog.MiningType.BULK, catalog.events().getFirst().miningType());
    }
}
