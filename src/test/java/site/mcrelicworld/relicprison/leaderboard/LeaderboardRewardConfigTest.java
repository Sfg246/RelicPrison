package site.mcrelicworld.relicprison.leaderboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LeaderboardRewardConfigTest {
    @TempDir Path temp;

    @Test
    void parsesListRewardsAndNormalizesBoardIds() throws Exception {
        Files.writeString(temp.resolve("leaderboard-rewards.yml"), """
                file-version: 1
                enabled: true
                settings:
                  announce-rewards: true
                  offline-message: pending
                boards:
                  daily-blocks:
                    enabled: true
                    metric: blocks
                    period: daily
                    rewards:
                      first:
                        positions: [1]
                        money: 25.50
                        items:
                          - id: DIAMOND
                            amount: 2
                        boosters:
                          - scope: personal
                            multiplier: 1.5
                            duration: 10m
                        commands:
                          - "say %player%"
                """);

        LeaderboardRewardConfig config = LeaderboardRewardConfig.load(temp.toFile());

        assertTrue(config.enabled());
        LeaderboardRewardConfig.RewardDefinition reward = config.rewards("daily-blocks", 1).getFirst();
        assertEquals(0, new BigDecimal("25.50").compareTo(reward.money()));
        assertEquals(2, reward.items().getFirst().amount());
        assertEquals(600_000L, reward.boosters().getFirst().durationMillis());
        assertEquals("say %player%", reward.commands().getFirst());
    }

    @Test
    void duplicateNativeMoneyAndEconomyCommandProducesWarning() throws Exception {
        Files.writeString(temp.resolve("leaderboard-rewards.yml"), """
                file-version: 1
                enabled: true
                boards:
                  daily-blocks:
                    enabled: true
                    metric: blocks
                    period: daily
                    rewards:
                      first:
                        positions: [1]
                        money: 10000
                        commands:
                          - "eco give %player% 10000"
                """);

        LeaderboardRewardConfig config = LeaderboardRewardConfig.load(temp.toFile());

        assertEquals(1, config.warnings().size());
        assertEquals("daily_blocks", config.warnings().getFirst().boardId());
        assertEquals("first", config.warnings().getFirst().rewardId());
    }

    @Test
    void packagedDefaultDailyFirstPlacePaysNativeMoneyOnce() throws Exception {
        Files.copy(Path.of("src/main/resources/leaderboard-rewards.yml"),
                temp.resolve("leaderboard-rewards.yml"), StandardCopyOption.REPLACE_EXISTING);

        LeaderboardRewardConfig config = LeaderboardRewardConfig.load(temp.toFile());
        LeaderboardRewardConfig.RewardDefinition first = config.rewards("daily-blocks", 1).stream()
                .filter(reward -> reward.id().equals("first"))
                .findFirst()
                .orElseThrow();

        assertEquals(0, new BigDecimal("10000").compareTo(first.money()));
        assertTrue(first.commands().isEmpty());
        assertTrue(config.warnings().isEmpty());
    }
}
