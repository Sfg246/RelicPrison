package site.mcrelicworld.relicprison.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService.EditRequest;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService.Editor;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService.Operation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminGuiEditorServiceTest {
    @TempDir Path tempDir;
    private AdminGuiEditorService service;
    private final List<String> reloads = new ArrayList<>();
    private final List<String> audits = new ArrayList<>();
    private final UUID staff = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() throws Exception {
        write("ranks.yml", """
                file-version: 1
                ranks:
                  a:
                    display-name: A
                    order: 0
                    next-cost: '100'
                    sell-multiplier: '1.0'
                    luckperms-group: a
                    enabled: true
                  z:
                    display-name: Z
                    order: 1
                    next-cost: '0'
                    sell-multiplier: '1.0'
                    luckperms-group: z
                    enabled: true
                """);
        write("prestiges.yml", """
                file-version: 1
                prestiges:
                  p1:
                    display-name: P1
                    order: 0
                    cost: '1000'
                    sell-multiplier: '1.1'
                    rank-cost-multiplier: '1.0'
                    luckperms-group: p1
                    enabled: true
                """);
        write("sell-prices.yml", """
                file-version: 1
                prices:
                  STONE: '1'
                custom-prices: {}
                """);
        write("boosters.yml", """
                settings:
                  stacking-mode: MULTIPLICATIVE
                  maximum-final-multiplier: '100'
                  maximum-booster-multiplier: '10'
                  maximum-duration-seconds: 86400
                  offline-time-continues: true
                  strength-stacks: true
                  duration-stacks: false
                item:
                  material: NETHER_STAR
                  name: '&dBooster'
                  lore: []
                  custom-model-data: 0
                permission-multipliers:
                  relicprison.booster.test: '1.5'
                """);
        write("block-events.yml", """
                events:
                  starter:
                    enabled: true
                    priority: 0
                    trigger: chance-per-action
                    mining-type: both
                    chance: 1.0
                    cooldown-seconds: 0
                    blocks: [STONE]
                    mines: [a]
                    permissions: []
                    per-action-command-limit: 4
                    per-action-reward-limit: 16
                    rewards:
                      money: '1'
                """);
        write("mines.yml", """
                file-version: 1
                mines:
                  a:
                    display-name: A
                    world:
                      uuid: 00000000-0000-0000-0000-000000000001
                      name: world
                    minimum: {x: 0, y: 0, z: 0}
                    maximum: {x: 10, y: 10, z: 10}
                    enabled: true
                    sort-order: 0
                    spawn:
                      present: false
                    composition:
                      - provider: vanilla
                        block: STONE
                        weight: 1.0
                    reset:
                      timed-enabled: true
                      interval-seconds: 900
                      percentage-enabled: true
                      mined-percentage: 80.0
                      warning-seconds: [30, 10]
                      notification-scope: MINE
                      notification-radius: 64
                      evacuate-players: true
                      order: CHUNK_GROUPED_BOTTOM_UP
                """);
        service = new AdminGuiEditorService(tempDir, editor -> reloads.add(editor.name()),
                (staffId, staffName, editor, operation, targetId, before, after, success, reason) ->
                        audits.add(editor + ":" + operation + ":" + success + ":" + reason),
                () -> Set.of("a"), () -> false);
    }

    @Test
    void snapshotsSupportSearchAndPagination() throws Exception {
        var snapshot = service.snapshot(Editor.RANKS, 0, "A");
        assertEquals(1, snapshot.entries().size());
        assertEquals("a", snapshot.entries().getFirst().id());
        assertEquals(1, snapshot.pages());
    }

    @Test
    void snapshotsPaginateLargeEditorLists() throws Exception {
        StringBuilder yaml = new StringBuilder("file-version: 1\nranks:\n");
        for (int index = 0; index < 40; index++) {
            yaml.append("  rank").append(index).append(": {display-name: Rank ").append(index)
                    .append(", order: ").append(index).append(", next-cost: '1'}\n");
        }
        write("ranks.yml", yaml.toString());

        var first = service.snapshot(Editor.RANKS, 0, "");
        var second = service.snapshot(Editor.RANKS, 1, "");

        assertEquals(2, first.pages());
        assertEquals(36, first.entries().size());
        assertEquals(4, second.entries().size());
    }

    @Test
    void editsEveryApprovedEditorAndAuditsSuccess() throws Exception {
        assertTrue(apply(Editor.RANKS, Operation.SET_PROPERTY, "a", "next-cost", "250").success());
        assertTrue(apply(Editor.PRESTIGES, Operation.SET_PROPERTY, "p1", "cost", "2000").success());
        assertTrue(apply(Editor.SELL_PRICES, Operation.SET_PROPERTY, "STONE", "price", "3.5").success());
        assertTrue(apply(Editor.BOOSTERS, Operation.SET_PROPERTY, "settings", "maximum-final-multiplier", "50").success());
        assertTrue(apply(Editor.BLOCK_EVENTS, Operation.SET_PROPERTY, "starter", "chance", "0.5").success());
        assertTrue(apply(Editor.RESET_SETTINGS, Operation.SET_PROPERTY, "a", "interval-seconds", "1200").success());
        assertTrue(apply(Editor.MINE_COMPOSITION, Operation.SET_PROPERTY, "a", "DIAMOND_ORE", "2.0").success());
        assertTrue(reloads.containsAll(List.of("RANKS", "PRESTIGES", "SELL_PRICES", "BOOSTERS",
                "BLOCK_EVENTS", "RESET_SETTINGS", "MINE_COMPOSITION")));
        assertEquals(7, audits.stream().filter(value -> value.contains(":true:")).count());
    }

    @Test
    void createToggleDeleteAndCancelAreBehavioralOperations() throws Exception {
        var created = apply(Editor.RANKS, Operation.CREATE, "b", "", "0");
        assertTrue(created.success());
        assertTrue(service.details(Editor.RANKS, "b").values().stream().anyMatch(line -> line.contains("next-cost=0")));
        assertTrue(apply(Editor.RANKS, Operation.TOGGLE_ENABLED, "b", "", "").success());
        assertTrue(apply(Editor.RANKS, Operation.DELETE, "b", "", "").success());
        assertFalse(service.apply(new EditRequest(Editor.RANKS, Operation.CANCEL, "a", "", "", "", staff, "Admin")).success());
    }

    @Test
    void staleRevisionRejectsWithoutOverwritingNewerConfiguration() throws Exception {
        String oldRevision = service.snapshot(Editor.RANKS, 0, "").revision();
        assertTrue(apply(Editor.RANKS, Operation.SET_PROPERTY, "a", "next-cost", "200").success());
        var stale = service.apply(new EditRequest(Editor.RANKS, Operation.SET_PROPERTY, "a", "next-cost", "300",
                oldRevision, staff, "Admin"));
        assertFalse(stale.success());
        assertEquals("stale-edit-conflict", stale.message());
        assertTrue(service.details(Editor.RANKS, "a").values().stream().anyMatch(line -> line.contains("next-cost=200")));
    }

    @Test
    void invalidValuesRollbackAndAuditFailure() throws Exception {
        String before = Files.readString(tempDir.resolve("sell-prices.yml"));
        var negative = apply(Editor.SELL_PRICES, Operation.SET_PROPERTY, "STONE", "price", "-1");
        assertFalse(negative.success());
        assertEquals(before, Files.readString(tempDir.resolve("sell-prices.yml")));
        assertTrue(audits.stream().anyMatch(value -> value.contains("SELL_PRICES:SET_PROPERTY:false")));

        assertFalse(apply(Editor.BOOSTERS, Operation.SET_PROPERTY, "settings", "maximum-final-multiplier", "NaN").success());
        assertFalse(apply(Editor.BOOSTERS, Operation.SET_PROPERTY, "settings", "maximum-final-multiplier", "Infinity").success());
        assertFalse(apply(Editor.RANKS, Operation.SET_PROPERTY, "a", "mine", "missing").success());
    }

    @Test
    void duplicateIdsAndMissingIntegrationsAreRejected() {
        assertFalse(apply(Editor.RANKS, Operation.CREATE, "a", "", "10").success());
        assertFalse(apply(Editor.SELL_PRICES, Operation.CREATE, "custom:itemsadder:block", "", "5").success());
        assertFalse(apply(Editor.MINE_COMPOSITION, Operation.SET_PROPERTY, "a", "itemsadder:namespace:block", "1.0").success());

        AdminGuiEditorService customBlocks = new AdminGuiEditorService(tempDir, editor -> { },
                (staffId, staffName, editor, operation, targetId, before, after, success, reason) -> { },
                () -> Set.of("a"), () -> true, id -> id.equals("itemsadder:valid"));
        assertFalse(customBlocks.apply(new EditRequest(Editor.SELL_PRICES, Operation.CREATE,
                "custom:itemsadder:missing", "", "5", "", staff, "Admin")).success());
        assertTrue(customBlocks.apply(new EditRequest(Editor.SELL_PRICES, Operation.CREATE,
                "custom:itemsadder:valid", "", "5", "", staff, "Admin")).success());
    }

    @Test
    void detailsExposeCurrentValuesAndEditableProperties() throws Exception {
        var details = service.details(Editor.BLOCK_EVENTS, "starter");
        assertEquals(Editor.BLOCK_EVENTS, details.editor());
        assertTrue(details.values().stream().anyMatch(line -> line.contains("trigger=chance-per-action")));
        assertTrue(details.editableProperties().contains("rewards.commands"));
        assertTrue(details.editableProperties().contains("rewards.announcements"));
        assertTrue(details.editableProperties().contains("rewards.booster.multiplier"));
    }

    @Test
    void everyEditorOpensAndOnlyImplementedCreateActionsAreAdvertised() throws Exception {
        for (Editor editor : Editor.values()) {
            var snapshot = service.snapshot(editor, 0, "");
            assertEquals(editor, snapshot.editor());
            assertFalse(snapshot.entries().isEmpty());
            assertEquals(editor, service.details(editor, snapshot.entries().getFirst().id()).editor());
        }
        assertTrue(service.supportsCreate(Editor.RANKS));
        assertTrue(service.supportsCreate(Editor.BOOSTERS));
        assertFalse(service.supportsCreate(Editor.RESET_SETTINGS));
        assertFalse(service.supportsCreate(Editor.MINE_COMPOSITION));
    }

    @Test
    void rankAndPrestigeMetadataIsEditableAndImpossibleLoopsAreRejected() throws Exception {
        assertTrue(apply(Editor.RANKS, Operation.SET_PROPERTY, "a", "display-material", "DIAMOND").success());
        assertTrue(apply(Editor.RANKS, Operation.SET_PROPERTY, "a", "lore", "First|Second").success());
        assertTrue(apply(Editor.RANKS, Operation.SET_PROPERTY, "z", "requirements", "rank:a|permission:test.rank").success());
        assertFalse(apply(Editor.RANKS, Operation.SET_PROPERTY, "a", "requirements", "rank:z").success());
        assertTrue(apply(Editor.PRESTIGES, Operation.CREATE, "p2", "", "2000").success());
        assertTrue(apply(Editor.PRESTIGES, Operation.SET_PROPERTY, "p2", "requirements", "prestige:p1").success());
        assertFalse(apply(Editor.PRESTIGES, Operation.SET_PROPERTY, "p1", "requirements", "prestige:p2").success());
        assertTrue(apply(Editor.RANKS, Operation.SET_PROPERTY, "a", "id", "start").success());
        assertEquals("start", service.snapshot(Editor.RANKS, 0, "start").entries().getFirst().id());
    }

    @Test
    void compositionPreservesPrestigeAirAndProviderMetadata() throws Exception {
        assertTrue(apply(Editor.MINE_COMPOSITION, Operation.SET_PROPERTY, "a", "AIR",
                "5 minimum-prestige=p1 allow-air=true").success());
        AdminGuiEditorService customBlocks = new AdminGuiEditorService(tempDir, editor -> reloads.add(editor.name()),
                (staffId, staffName, editor, operation, targetId, before, after, success, reason) -> { },
                () -> Set.of("a"), () -> true, id -> id.equals("namespace:block"));
        assertTrue(customBlocks.apply(new EditRequest(Editor.MINE_COMPOSITION, Operation.SET_PROPERTY, "a",
                "itemsadder:namespace:block", "2 fallback-material=STONE", "", staff, "Admin")).success());
        var values = service.details(Editor.MINE_COMPOSITION, "a").values();
        assertTrue(values.stream().anyMatch(line -> line.contains("minimum-prestige=p1")));
        assertTrue(values.stream().anyMatch(line -> line.contains("allow-air=true")));
        assertTrue(values.stream().anyMatch(line -> line.contains("fallback-material=STONE")));
    }

    @Test
    void resetRuntimeFieldsAndBlockEventTriggerFieldsPersist() throws Exception {
        assertTrue(apply(Editor.RESET_SETTINGS, Operation.SET_PROPERTY, "a", "retry-count", "4").success());
        assertTrue(apply(Editor.RESET_SETTINGS, Operation.SET_PROPERTY, "a", "retry-delay-seconds", "15").success());
        assertTrue(apply(Editor.RESET_SETTINGS, Operation.SET_PROPERTY, "a", "countdown-seconds", "45").success());
        assertTrue(apply(Editor.RESET_SETTINGS, Operation.SET_PROPERTY, "a", "teleport-destination", "WORLD_SPAWN").success());
        assertTrue(apply(Editor.BLOCK_EVENTS, Operation.SET_PROPERTY, "starter", "every-x-blocks", "50").success());
        assertTrue(apply(Editor.BLOCK_EVENTS, Operation.SET_PROPERTY, "starter", "rewards.announcements", "Winner!").success());
        assertTrue(service.details(Editor.RESET_SETTINGS, "a").values().stream()
                .anyMatch(line -> line.contains("retry-count=4")));
        assertFalse(apply(Editor.RESET_SETTINGS, Operation.SET_PROPERTY, "a", "countdown-seconds", "5").success());
    }

    @Test
    void failedRuntimeReloadRestoresFileAndAuditsFailure() throws Exception {
        String original = Files.readString(tempDir.resolve("sell-prices.yml"));
        AtomicInteger reloadAttempts = new AtomicInteger();
        AdminGuiEditorService failing = new AdminGuiEditorService(tempDir, editor -> {
            if (reloadAttempts.getAndIncrement() == 0) throw new IllegalStateException("reload rejected");
        }, (staffId, staffName, editor, operation, targetId, before, after, success, reason) ->
                audits.add(editor + ":" + operation + ":" + success + ":" + reason),
                () -> Set.of("a"), () -> false);

        var result = failing.apply(new EditRequest(Editor.SELL_PRICES, Operation.SET_PROPERTY, "STONE", "price",
                "9", "", staff, "Admin"));

        assertFalse(result.success());
        assertEquals(original, Files.readString(tempDir.resolve("sell-prices.yml")));
        assertEquals(2, reloadAttempts.get());
        assertTrue(Files.exists(tempDir.resolve("sell-prices.yml.rollback")));
        assertTrue(audits.stream().anyMatch(value -> value.contains("reload rejected")));
    }

    private AdminGuiEditorService.EditResult apply(Editor editor, Operation operation, String target, String property, String value) {
        return service.apply(new EditRequest(editor, operation, target, property, value, "", staff, "Admin"));
    }

    private void write(String file, String content) throws Exception {
        Files.writeString(tempDir.resolve(file), content);
    }
}
