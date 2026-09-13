package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.gang.Gang;
import site.mcrelicworld.relicprison.gang.GangBankTransaction;
import site.mcrelicworld.relicprison.gang.GangBooster;
import site.mcrelicworld.relicprison.gang.GangConfig;
import site.mcrelicworld.relicprison.gang.GangInvite;
import site.mcrelicworld.relicprison.gang.GangJoinMode;
import site.mcrelicworld.relicprison.gang.GangMissionState;
import site.mcrelicworld.relicprison.gang.GangOperationResult;
import site.mcrelicworld.relicprison.gang.GangPermission;
import site.mcrelicworld.relicprison.gang.GangRank;
import site.mcrelicworld.relicprison.gang.GangRepository;
import site.mcrelicworld.relicprison.gang.migration.GangMigrationProvider;
import site.mcrelicworld.relicprison.gang.migration.GangMigrationService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GangRepositoryBehaviorTest {
    @TempDir Path directory;

    @Test
    void createsGangWithDefaultRanksAndRejectsDuplicateIdentityAndMembership() throws Exception {
        try (DatabaseManager database = database(directory.resolve("create.db"))) {
            GangRepository repository = new GangRepository(database);
            UUID owner = UUID.randomUUID();
            Gang gang = create(repository, owner, "Alpha Gang", "AG", 10);

            assertEquals(owner, gang.ownerId());
            assertEquals(1, gang.memberCount());
            List<GangRank> ranks = get(repository.ranks(gang.id()));
            assertEquals(6, ranks.size());
            GangRank ownerRank = ranks.stream().filter(GangRank::owner).findFirst().orElseThrow();
            assertEquals(GangPermission.ownerPermissions(), ownerRank.permissions());
            assertTrue(get(repository.member(owner)).isPresent());

            assertThrows(ExecutionException.class,
                    () -> repository.create(UUID.randomUUID(), "alpha gang", "OTHER", 10, ranks(), now())
                            .get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,
                    () -> repository.create(UUID.randomUUID(), "Other", "ag", 10, ranks(), now())
                            .get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,
                    () -> repository.create(owner, "Second", "SEC", 10, ranks(), now())
                            .get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void invitationAcceptanceDenialExpiryOpenJoinLimitsLeaveAndKickAreBehavioral() throws Exception {
        try (DatabaseManager database = database(directory.resolve("membership.db"))) {
            GangRepository repository = new GangRepository(database);
            UUID owner = UUID.randomUUID();
            Gang gang = create(repository, owner, "Membership", "MEM", 3);
            UUID accepted = UUID.randomUUID();
            GangInvite invite = get(repository.invite(gang.id(), accepted, owner, now() + 60_000L, now()));
            assertTrue(get(repository.respondInvite(invite.id(), accepted, true, now())).success());
            assertTrue(get(repository.member(accepted)).isPresent());

            UUID denied = UUID.randomUUID();
            GangInvite deniedInvite = get(repository.invite(gang.id(), denied, owner, now() + 60_000L, now()));
            assertTrue(get(repository.respondInvite(deniedInvite.id(), denied, false, now())).success());
            assertTrue(get(repository.member(denied)).isEmpty());

            UUID expired = UUID.randomUUID();
            GangInvite expiredInvite = get(repository.invite(gang.id(), expired, owner, now() - 1L, now() - 100L));
            GangOperationResult expiredResult = get(repository.respondInvite(expiredInvite.id(), expired, true, now()));
            assertFalse(expiredResult.success());
            assertTrue(get(repository.invites(expired, now())).isEmpty());

            get(repository.editGang(gang.id(), owner,
                    new GangRepository.Edit(GangRepository.EditField.JOIN_MODE, GangJoinMode.OPEN.name()), now()));
            UUID openMember = UUID.randomUUID();
            assertTrue(get(repository.joinOpen(gang.id(), openMember, now())).success());
            assertFalse(get(repository.joinOpen(gang.id(), UUID.randomUUID(), now())).success());
            assertTrue(get(repository.removeMember(gang.id(), accepted, accepted, true, now())).success());
            assertTrue(get(repository.removeMember(gang.id(), openMember, owner, false, now())).success());
            assertFalse(get(repository.removeMember(gang.id(), owner, owner, true, now())).success());
        }
    }

    @Test
    void ownershipAndCustomRankOperationsPreserveHierarchyAndOwnerPermissions() throws Exception {
        try (DatabaseManager database = database(directory.resolve("ranks.db"))) {
            GangRepository repository = new GangRepository(database);
            UUID owner = UUID.randomUUID();
            UUID successor = UUID.randomUUID();
            Gang gang = create(repository, owner, "Ranks", "RNK", 10);
            GangInvite invite = get(repository.invite(gang.id(), successor, owner, now() + 10_000L, now()));
            get(repository.respondInvite(invite.id(), successor, true, now()));

            GangRank custom = get(repository.createRank(gang.id(), owner, "Miner", 20, "&b",
                    EnumSet.of(GangPermission.DEPOSIT_BANK), now()));
            assertTrue(get(repository.setRankPermission(gang.id(), custom.id(), owner,
                    GangPermission.INVITE, true, now())).success());
            assertTrue(get(repository.setMemberRank(gang.id(), successor, custom.id(), owner, now())).success());
            GangRank fallback = get(repository.ranks(gang.id())).stream()
                    .filter(rank -> rank.systemKey().equals("recruit")).findFirst().orElseThrow();
            assertTrue(get(repository.deleteRank(gang.id(), custom.id(), fallback.id(), owner, now())).success());
            assertEquals(fallback.id(), get(repository.member(successor)).orElseThrow().rankId());

            GangRank ownerRank = get(repository.ranks(gang.id())).stream().filter(GangRank::owner).findFirst().orElseThrow();
            assertFalse(get(repository.setRankPermission(gang.id(), ownerRank.id(), owner,
                    GangPermission.DISBAND, false, now())).success());
            assertTrue(get(repository.transferOwnership(gang.id(), owner, successor, now())).success());
            Gang transferred = get(repository.findById(gang.id())).orElseThrow();
            assertEquals(successor, transferred.ownerId());
            assertNotEquals(ownerRank.id(), get(repository.member(owner)).orElseThrow().rankId());
            assertEquals(ownerRank.id(), get(repository.member(successor)).orElseThrow().rankId());
        }
    }

    @Test
    void bankTransactionsAreIdempotentAndConcurrentWithdrawalsCannotDoubleSpend() throws Exception {
        Path file = directory.resolve("bank.db");
        try (DatabaseManager first = database(file); DatabaseManager second = database(file)) {
            GangRepository primary = new GangRepository(first);
            GangRepository competing = new GangRepository(second);
            Gang gang = create(primary, UUID.randomUUID(), "Bankers", "BNK", 10);
            UUID player = gang.ownerId();
            GangBankTransaction deposit = get(primary.changeBank(gang.id(), player, new BigDecimal("100.00"),
                    GangBankTransaction.Type.DEPOSIT, "seed", "deposit-one", new BigDecimal("1000"),
                    BigDecimal.ZERO, "today", now()));
            GangBankTransaction duplicate = get(primary.changeBank(gang.id(), player, new BigDecimal("100.00"),
                    GangBankTransaction.Type.DEPOSIT, "seed", "deposit-one", new BigDecimal("1000"),
                    BigDecimal.ZERO, "today", now()));
            assertEquals(deposit.id(), duplicate.id());

            CompletableFuture<GangBankTransaction> left = primary.changeBank(gang.id(), player,
                    new BigDecimal("-80"), GangBankTransaction.Type.WITHDRAWAL, "left", "withdraw-left",
                    BigDecimal.ZERO, BigDecimal.ZERO, "today", now());
            CompletableFuture<GangBankTransaction> right = competing.changeBank(gang.id(), player,
                    new BigDecimal("-80"), GangBankTransaction.Type.WITHDRAWAL, "right", "withdraw-right",
                    BigDecimal.ZERO, BigDecimal.ZERO, "today", now());
            int successes = 0;
            for (CompletableFuture<GangBankTransaction> future : List.of(left, right)) {
                try { future.get(15, TimeUnit.SECONDS); successes++; }
                catch (ExecutionException ignored) { }
            }
            assertEquals(1, successes);
            assertEquals(0, new BigDecimal("20").compareTo(
                    get(primary.findById(gang.id())).orElseThrow().bankBalance()));
            assertEquals(2, get(primary.bankHistory(gang.id(), 10)).size());
        }
    }

    @Test
    void upgradesProgressionContributionsBoostersMissionsAndLeaderboardsPersist() throws Exception {
        try (DatabaseManager database = database(directory.resolve("systems.db"))) {
            GangRepository repository = new GangRepository(database);
            GangConfig config = config();
            Gang gang = create(repository, UUID.randomUUID(), "Progress", "XP", 10);
            get(repository.adjustProgression(gang.id(), gang.ownerId(), GangRepository.AdminNumericField.POINTS,
                    new BigDecimal("100"), false, now()));
            GangConfig.UpgradeDefinition upgrade = config.upgrades().get("capacity");
            assertEquals(1, get(repository.purchaseUpgrade(gang.id(), gang.ownerId(), upgrade,
                    "upgrade-one", 100, now())).tier());
            assertEquals(15, get(repository.findById(gang.id())).orElseThrow().memberLimit());
            long seasonNow = now();
            get(repository.createSeason("season-one", seasonNow - 10_000L, seasonNow + 10_000L,
                    List.of("level", "blocks"), "tier:1", seasonNow));

            GangRepository.ContributionResult first = get(repository.recordContribution(gang.ownerId(),
                    GangConfig.ContributionType.BLOCKS, new BigDecimal("3"), new BigDecimal("12"), "stone",
                    "contribution-one", config, "daily", now()));
            assertTrue(first.applied());
            assertEquals(2, first.newLevel());
            assertEquals(List.of("daily_blocks"), first.completedMissions());
            assertTrue(get(repository.recordContribution(gang.ownerId(), GangConfig.ContributionType.BLOCKS,
                    new BigDecimal("3"), new BigDecimal("12"), "stone", "contribution-one", config,
                    "daily", now())).duplicate());
            assertEquals(3L, get(repository.statistics(gang.id())).blocks());
            assertEquals(3L, get(repository.memberStatistics(gang.id())).getFirst().blocks());
            assertEquals(GangMissionState.State.COMPLETED,
                    get(repository.missions(gang.id(), config, "daily", now())).getFirst().state());
            assertTrue(get(repository.claimMission(gang.id(), "daily_blocks", "daily", gang.ownerId(),
                    config, now())).success());
            assertEquals(95L, get(repository.findById(gang.id())).orElseThrow().points());

            GangBooster booster = get(repository.activateBooster(gang.id(), GangBooster.Type.GANG_XP,
                    new BigDecimal("2"), now(), now() + 60_000L, gang.ownerId(), "test", now()));
            assertTrue(booster.active(now()));
            assertEquals(1, get(repository.activeBoosters(now())).size());
            assertEquals(gang.id(), get(repository.leaderboard("level", 10)).getFirst().gangId());
            assertEquals(2, get(repository.finalizeSeason("season-one", 3, seasonNow + 20_000L)).size());
            assertEquals(2, get(repository.finalizeSeason("season-one", 3, seasonNow + 20_000L)).size());
            assertEquals(2, get(repository.seasonResults("season-one")).size());
        }
    }

    @Test
    void restartPersistenceDisbandAndHistoricalAuditRemainAvailable() throws Exception {
        Path file = directory.resolve("restart.db");
        UUID gangId;
        UUID owner = UUID.randomUUID();
        try (DatabaseManager database = database(file)) {
            GangRepository repository = new GangRepository(database);
            Gang gang = create(repository, owner, "Persistent", "PST", 10);
            gangId = gang.id();
            get(repository.changeBank(gangId, owner, BigDecimal.TEN, GangBankTransaction.Type.DEPOSIT,
                    "persist", "persist-bank", BigDecimal.ZERO, BigDecimal.ZERO, "today", now()));
        }
        try (DatabaseManager database = database(file)) {
            GangRepository repository = new GangRepository(database);
            assertEquals(BigDecimal.TEN, get(repository.findById(gangId)).orElseThrow().bankBalance());
            assertTrue(get(repository.disband(gangId, owner, now())).success());
            assertFalse(get(repository.findById(gangId)).orElseThrow().enabled());
            assertTrue(get(repository.member(owner)).isEmpty());
            assertFalse(get(repository.audit(gangId, 20)).isEmpty());
            assertEquals(1, get(repository.bankHistory(gangId, 20)).size());
        }
    }

    @Test
    void twoServersCannotJoinDifferentGangsWithOnePlayer() throws Exception {
        Path file = directory.resolve("membership-race.db");
        try (DatabaseManager first = database(file); DatabaseManager second = database(file)) {
            GangRepository left = new GangRepository(first);
            GangRepository right = new GangRepository(second);
            Gang alpha = create(left, UUID.randomUUID(), "Alpha", "A1", 10);
            Gang beta = create(left, UUID.randomUUID(), "Beta", "B1", 10);
            get(left.editGang(alpha.id(), alpha.ownerId(), new GangRepository.Edit(
                    GangRepository.EditField.JOIN_MODE, GangJoinMode.OPEN.name()), now()));
            get(left.editGang(beta.id(), beta.ownerId(), new GangRepository.Edit(
                    GangRepository.EditField.JOIN_MODE, GangJoinMode.OPEN.name()), now()));
            UUID player = UUID.randomUUID();
            CompletableFuture<GangOperationResult> firstJoin = left.joinOpen(alpha.id(), player, now());
            CompletableFuture<GangOperationResult> secondJoin = right.joinOpen(beta.id(), player, now());
            GangOperationResult one = get(firstJoin);
            GangOperationResult two = get(secondJoin);
            assertNotEquals(one.success(), two.success());
            assertTrue(get(left.member(player)).isPresent());
        }
    }

    @Test
    void migrationRequiresPreviewAndMapsProviderMembers() throws Exception {
        try (DatabaseManager database = database(directory.resolve("migration.db"))) {
            GangRepository repository = new GangRepository(database);
            UUID owner = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            GangMigrationProvider provider = new GangMigrationProvider() {
                @Override public String providerId() { return "test-factions"; }
                @Override public String providerVersion() { return "1"; }
                @Override public boolean available() { return true; }
                @Override public List<SourceGang> readPreview() {
                    return List.of(new SourceGang("source-one", "Imported", "IMP", owner,
                            List.of(new SourceMember(owner, "leader"), new SourceMember(member, "moderator"))));
                }
            };
            GangMigrationService migration = new GangMigrationService(repository, config());
            GangMigrationService.Preview preview = migration.preview(provider);
            assertEquals(1, preview.gangs().size());
            assertEquals(1, get(migration.confirm(preview.token())).imported());
            assertTrue(get(repository.member(owner)).isPresent());
            assertTrue(get(repository.member(member)).isPresent());
            assertThrows(ExecutionException.class,
                    () -> migration.confirm(preview.token()).get(5, TimeUnit.SECONDS));
        }
    }

    private static Gang create(GangRepository repository, UUID owner, String name, String tag, int limit)
            throws Exception {
        return get(repository.create(owner, name, tag, limit, ranks(), now()));
    }

    private static List<GangConfig.DefaultRank> ranks() {
        return List.of(
                rank("owner", 100, GangPermission.ownerPermissions()),
                rank("co_leader", 90, EnumSet.of(GangPermission.INVITE, GangPermission.KICK)),
                rank("officer", 70, EnumSet.of(GangPermission.INVITE)),
                rank("veteran", 50, EnumSet.of(GangPermission.DEPOSIT_BANK)),
                rank("member", 30, EnumSet.of(GangPermission.DEPOSIT_BANK)),
                rank("recruit", 10, EnumSet.of(GangPermission.DEPOSIT_BANK)));
    }

    private static GangConfig.DefaultRank rank(String key, int priority, Set<GangPermission> permissions) {
        return new GangConfig.DefaultRank(key, key, priority, "&7", permissions);
    }

    private static GangConfig config() {
        Map<GangConfig.ContributionType, BigDecimal> sources = new EnumMap<>(GangConfig.ContributionType.class);
        for (GangConfig.ContributionType type : GangConfig.ContributionType.values()) sources.put(type, BigDecimal.ONE);
        GangConfig.UpgradeDefinition upgrade = new GangConfig.UpgradeDefinition("capacity", "Capacity",
                GangConfig.CostType.POINTS, List.of(new GangConfig.UpgradeTier(new BigDecimal("10"),
                Map.of("member_capacity", new BigDecimal("5")))), List.of());
        GangConfig.MissionDefinition mission = new GangConfig.MissionDefinition("daily_blocks", "Daily Blocks",
                GangConfig.MissionObjective.BLOCKS, "", new BigDecimal("3"), GangConfig.ResetPeriod.DAILY,
                new GangConfig.MissionReward(BigDecimal.TEN, 5L, BigDecimal.ONE, null, BigDecimal.ONE,
                        Duration.ZERO, List.of()));
        return new GangConfig(true, BigDecimal.ZERO, "[A-Za-z0-9 ]+", 2, 24, "[A-Za-z0-9]+", 1, 6,
                10, 100, Map.of(), Duration.ofHours(1), new BigDecimal("1000"), BigDecimal.ZERO, 10,
                BigDecimal.TEN, new BigDecimal("2"), sources, ranks(), Map.of("capacity", upgrade),
                Map.of("daily_blocks", mission), new BigDecimal("5"), Duration.ofDays(1),
                List.of("level", "xp", "blocks", "money", "prestiges", "balance", "block_events"),
                false, "", "none");
    }

    private static DatabaseManager database(Path file) throws Exception {
        DatabaseManager database = new DatabaseManager(Logger.getLogger("GangRepositoryBehaviorTest"),
                new StorageConfig(StorageConfig.Type.SQLITE, file, "localhost", 3306, "test", "test", "",
                        "", 1, 1000, 2, 10, 15, 20, 5));
        database.initializeAsync().get(10, TimeUnit.SECONDS);
        return database;
    }

    private static long now() { return System.currentTimeMillis(); }

    private static <T> T get(CompletableFuture<T> future) throws Exception {
        return future.get(15, TimeUnit.SECONDS);
    }
}
