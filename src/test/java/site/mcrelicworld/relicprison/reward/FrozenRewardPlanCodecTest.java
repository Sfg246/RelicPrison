package site.mcrelicworld.relicprison.reward;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.reward.RewardLedgerService.ComponentDraft;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FrozenRewardPlanCodecTest {
    @Test
    void freezesEverySupportedRewardComponentWithoutConfigurationLookup() {
        long dueAt = 123456789L;
        List<ComponentDraft> expected = List.of(
                ComponentDraft.money("money", new BigDecimal("12.50"), dueAt),
                ComponentDraft.experience("xp", 75, dueAt),
                ComponentDraft.vanillaItem("item", Material.DIAMOND, 3, dueAt),
                ComponentDraft.itemsAdderItem("custom", "relic:token", 2, dueAt),
                ComponentDraft.booster("booster", false, new BigDecimal("1.5"), 60_000L, "tester", dueAt),
                ComponentDraft.consoleCommand("command", "give Player diamond 1", dueAt),
                ComponentDraft.announcement("announcement", "Player won", dueAt));

        assertEquals(expected, FrozenRewardPlanCodec.decode(FrozenRewardPlanCodec.encode(expected)));
    }

    @Test
    void rejectsLegacyMetadataThatCannotReconstructRewards() {
        assertThrows(IllegalArgumentException.class,
                () -> FrozenRewardPlanCodec.decode("event=changed;blocks=1"));
    }
}
