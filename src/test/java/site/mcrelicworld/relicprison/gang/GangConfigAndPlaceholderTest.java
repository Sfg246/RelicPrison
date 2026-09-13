package site.mcrelicworld.relicprison.gang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GangConfigAndPlaceholderTest {
    @Test
    void parsesValidatedConfigurationSections() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                creation: {cost: '25'}
                validation:
                  name: {pattern: '[A-Za-z]+', minimum: 2, maximum: 12}
                  tag: {pattern: '[A-Z]+', minimum: 1, maximum: 4}
                members: {default-limit: 5, maximum-limit: 20}
                invites: {timeout: 2h}
                bank: {capacity: '1000', daily-withdrawal-limit: '100'}
                progression:
                  maximum-level: 10
                  base-xp: '100'
                  growth: '2'
                boosters: {maximum-multiplier: '3', maximum-duration: 1d}
                """);

        GangConfig config = GangConfigRepository.parse(yaml);

        assertEquals(new BigDecimal("25"), config.creationCost());
        assertEquals(5, config.defaultMemberLimit());
        assertEquals(new BigDecimal("200"), config.requiredXp(2));
        assertEquals(6, config.defaultRanks().size());
        assertTrue(config.defaultRanks().stream().filter(rank -> rank.key().equals("owner"))
                .findFirst().orElseThrow().permissions().contains(GangPermission.DISBAND));
    }

    @Test
    void dottedPermissionLimitsParseAndPassStartupValidation() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                members:
                  default-limit: 10
                  maximum-limit: 100
                  permission-limits:
                    relicprison.gang.limit.20: 20
                    relicprison.gang.limit.30: 30
                leaderboards:
                  categories: [level]
                """);

        GangConfig config = GangConfigRepository.parseValidated(yaml);

        assertEquals(20, config.memberPermissionLimits().get("relicprison.gang.limit.20"));
        assertEquals(30, config.memberPermissionLimits().get("relicprison.gang.limit.30"));
    }

    @Test
    void permissionLimitValidationIdentifiesPermissionValueAndRange() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                members:
                  default-limit: 10
                  maximum-limit: 100
                  permission-limits:
                    relicprison.gang.limit.invalid: 101
                leaderboards:
                  categories: [level]
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> GangConfigRepository.parseValidated(yaml));

        assertEquals("Gang permission member limit for 'relicprison.gang.limit.invalid' is 101; "
                + "allowed range is [10, 100]", error.getMessage());
    }

    @Test
    void resolvesGangAndContributionPlaceholdersWithoutDatabaseIo() {
        UUID gangId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID rankId = UUID.randomUUID();
        Gang gang = new Gang(gangId, "Diggers", "DIG", "", playerId, 1L, 7,
                new BigDecimal("12.5"), 42L, new BigDecimal("99.50"), 10, 3,
                GangJoinMode.OPEN, "&a", "mine", null, true, 1L, 1L);
        GangRank rank = new GangRank(rankId, gangId, "member", "Member", 30, "&b", true,
                Set.of(GangPermission.DEPOSIT_BANK), 1L, 1L);
        GangStatistics statistics = new GangStatistics(gangId, 100L, new BigDecimal("50"),
                new BigDecimal("20"), 2L, 1L, 3L, 1L);
        GangMemberStatistics contribution = new GangMemberStatistics(gangId, playerId, 40L,
                new BigDecimal("10"), new BigDecimal("8"), 1L, 0L, 2L, 1L);

        assertEquals("Diggers", GangPlaceholderValues.resolve("gang_name", gang, rank, statistics,
                contribution, 2, Map.of("blocks", 4), BigDecimal::toPlainString, "none"));
        assertEquals("40", GangPlaceholderValues.resolve("gang_contribution_blocks", gang, rank, statistics,
                contribution, 2, Map.of("blocks", 4), BigDecimal::toPlainString, "none"));
        assertEquals("4", GangPlaceholderValues.resolve("gang_leaderboard_position_blocks", gang, rank,
                statistics, contribution, 2, Map.of("blocks", 4), BigDecimal::toPlainString, "none"));
        assertEquals("none", GangPlaceholderValues.resolve("gang_name", null, null, null, null,
                0, Map.of(), BigDecimal::toPlainString, "none"));
    }
}
