package site.mcrelicworld.relicprison.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhaseFiveSixSourceTest {
    @Test
    void placeholdersUseCachesAndContainAllDocumentedKeys() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/placeholder/RelicPrisonExpansion.java"));

        for (String key : new String[]{"rank_progress_percent", "rank_money_remaining",
                "prestige_money_remaining", "mine_remaining_percent", "mine_reset_state",
                "mine_reset_count", "mine_last_reset_duration", "inventory_base_value",
                "inventory_final_value", "hand_base_value", "hand_final_value",
                "personal_booster_multiplier", "server_booster_multiplier", "combined_multiplier",
                "blocks_material_", "blocks_mine_", "normal_blocks", "bulk_blocks", "leaderboard_"}) {
            assertTrue(source.contains(key), key);
        }
        assertTrue(source.contains("cachedLeaderboard"));
        assertFalse(source.contains("java.sql"));
    }

    @Test
    void guiActionsAreSessionBoundAndCommonExploitClicksAreBlocked() throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/gui/PrisonGuiManager.java"));
        String listener = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/gui/PrisonGuiListener.java"));

        assertTrue(manager.contains("gui_action"));
        assertTrue(manager.contains("gui_session"));
        assertTrue(manager.contains("executeConfirmation"));
        assertTrue(manager.contains("rankPlan(player, profile, currentBalance(player)"));
        assertTrue(manager.contains("player.hasPermission"));
        assertTrue(listener.contains("NUMBER_KEY"));
        assertTrue(listener.contains("SWAP_OFFHAND"));
        assertTrue(listener.contains("DOUBLE_CLICK"));
        assertTrue(listener.contains("InventoryCreativeEvent"));
    }

    @Test
    void leaderboardsUseConfiguredOrderingAndRewardLedger() throws Exception {
        String statistics = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/statistics/StatisticsServiceImpl.java"));
        String service = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/leaderboard/LeaderboardRewardService.java"));
        String database = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/database/DatabaseManager.java"));

        assertTrue(statistics.contains("leaderboardCache"));
        assertTrue(statistics.contains("rankCase()"));
        assertTrue(statistics.contains("prestigeCase()"));
        assertTrue(statistics.contains("LOWER(last_name)"));
        assertTrue(service.contains("finalizeTransaction"));
        assertTrue(service.contains("RewardBundle"));
        assertTrue(service.contains("PACKAGE_CREATED"));
        assertTrue(service.contains("deliverDueAsync"));
        assertTrue(database.contains("rp_leaderboard_reward_periods"));
        assertTrue(database.contains("rp_leaderboard_reward_ledger"));
    }
}
