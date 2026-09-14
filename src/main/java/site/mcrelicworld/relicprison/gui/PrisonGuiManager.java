package site.mcrelicworld.relicprison.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService.EditRequest;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService.Editor;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService.EntryDetails;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService.EditorSnapshot;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService.Operation;
import site.mcrelicworld.relicprison.api.model.LeaderboardEntry;
import site.mcrelicworld.relicprison.booster.ActiveBooster;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.gang.Gang;
import site.mcrelicworld.relicprison.gang.GangAuditEntry;
import site.mcrelicworld.relicprison.gang.GangBankTransaction;
import site.mcrelicworld.relicprison.gang.GangBooster;
import site.mcrelicworld.relicprison.gang.GangLeaderboardEntry;
import site.mcrelicworld.relicprison.gang.GangMember;
import site.mcrelicworld.relicprison.gang.GangMemberStatistics;
import site.mcrelicworld.relicprison.gang.GangMissionState;
import site.mcrelicworld.relicprison.gang.GangRank;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.progression.PrestigeDefinition;
import site.mcrelicworld.relicprison.progression.RankDefinition;
import site.mcrelicworld.relicprison.util.ColorUtil;
import site.mcrelicworld.relicprison.util.DurationParser;

import java.math.BigDecimal;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Bedrock-safe chest menus. Every action is PDC/session-bound and revalidated server-side. */
public final class PrisonGuiManager {
    public enum Menu {
        MAIN,
        MINES,
        PROGRESSION,
        RANKUP_CONFIRM,
        RANKUP_MAX_CONFIRM,
        PRESTIGE,
        PRESTIGE_CONFIRM,
        DANGER_CONFIRM,
        SELLING,
        BOOSTERS,
        STATISTICS,
        LEADERBOARD,
        GANG,
        GANG_MEMBERS,
        GANG_MEMBER,
        GANG_RANKS,
        GANG_RANK_EDIT,
        GANG_RANK_PERMISSIONS,
        GANG_UPGRADES,
        GANG_BANK,
        GANG_STATS,
        GANG_CONTRIBUTIONS,
        GANG_BOOSTERS,
        GANG_MISSIONS,
        GANG_LEADERBOARDS,
        GANG_SETTINGS,
        GANG_AUDIT,
        ADMIN,
        ADMIN_MINES,
        ADMIN_COMPOSITION,
        ADMIN_RESET,
        ADMIN_RANKS,
        ADMIN_PRESTIGES,
        ADMIN_SELL,
        ADMIN_BOOSTERS,
        ADMIN_BLOCK_EVENTS,
        ADMIN_INTEGRATIONS,
        ADMIN_DIAGNOSTICS,
        ADMIN_PLAYERS
    }

    public static final class Holder implements InventoryHolder {
        private final Menu menu;
        private final int page;
        private final String context;
        private final UUID sessionId;
        private final long generation;
        private Inventory inventory;
        Holder(Menu menu, int page, String context, UUID sessionId, long generation) {
            this.menu = menu;
            this.page = page;
            this.context = context;
            this.sessionId = sessionId;
            this.generation = generation;
        }
        public Menu menu() { return menu; }
        public int page() { return page; }
        public String context() { return context; }
        public UUID sessionId() { return sessionId; }
        public long generation() { return generation; }
        void inventory(Inventory inventory) { this.inventory = inventory; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final RelicPrisonPlugin plugin;
    private final AdminGuiEditorService adminEditors;
    private final NamespacedKey actionKey;
    private final NamespacedKey sessionKey;
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ConfirmationContext> confirmations = new ConcurrentHashMap<>();
    private final Map<String, DangerConfirmation> dangerConfirmations = new ConcurrentHashMap<>();
    private final AdminInputTracker<AdminInputSession> adminInputs = new AdminInputTracker<>();
    private final Map<UUID, GangRankInput> gangRankInputs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClicks = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private volatile Map<Menu, String> titles = defaultTitles();
    private volatile GuiThemeConfig theme = GuiThemeConfig.defaults();

    public record Presentation(Map<Menu, String> titles, GuiThemeConfig theme) {
        public Presentation {
            titles = Map.copyOf(titles);
        }
    }

    public PrisonGuiManager(RelicPrisonPlugin plugin, AdminGuiEditorService adminEditors) {
        this.plugin = plugin;
        this.adminEditors = adminEditors;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.sessionKey = new NamespacedKey(plugin, "gui_session");
    }

    public void reload() throws Exception { apply(preview()); }

    public Presentation preview() throws Exception {
        EnumMap<Menu, String> loaded = new EnumMap<>(Menu.class);
        loaded.putAll(defaultTitles());
        loadTitle(loaded, Menu.MAIN, "guis/main.yml");
        loadTitle(loaded, Menu.MINES, "guis/mines.yml");
        loadTitle(loaded, Menu.PROGRESSION, "guis/progression.yml");
        loadTitleKey(loaded, Menu.RANKUP_CONFIRM, "guis/progression.yml", "rankup-confirm-title");
        loadTitleKey(loaded, Menu.RANKUP_MAX_CONFIRM, "guis/progression.yml", "rankup-max-confirm-title");
        loadTitle(loaded, Menu.PRESTIGE, "guis/prestige.yml");
        loadTitleKey(loaded, Menu.PRESTIGE_CONFIRM, "guis/prestige.yml", "prestige-confirm-title");
        loadTitleKey(loaded, Menu.DANGER_CONFIRM, "guis/theme.yml", "danger-confirm-title");
        loadTitle(loaded, Menu.SELLING, "guis/selling.yml");
        loadTitle(loaded, Menu.BOOSTERS, "guis/boosters.yml");
        loadTitle(loaded, Menu.STATISTICS, "guis/statistics.yml");
        loadTitleKey(loaded, Menu.LEADERBOARD, "guis/statistics.yml", "leaderboard-title");
        loadTitle(loaded, Menu.GANG, "guis/gangs.yml");
        loadTitleKey(loaded, Menu.GANG_MEMBERS, "guis/gangs.yml", "members-title");
        loadTitleKey(loaded, Menu.GANG_MEMBER, "guis/gangs.yml", "member-title");
        loadTitleKey(loaded, Menu.GANG_RANKS, "guis/gangs.yml", "ranks-title");
        loadTitleKey(loaded, Menu.GANG_RANK_EDIT, "guis/gangs.yml", "rank-edit-title");
        loadTitleKey(loaded, Menu.GANG_RANK_PERMISSIONS, "guis/gangs.yml", "rank-permissions-title");
        loadTitleKey(loaded, Menu.GANG_UPGRADES, "guis/gangs.yml", "upgrades-title");
        loadTitleKey(loaded, Menu.GANG_BANK, "guis/gangs.yml", "bank-title");
        loadTitleKey(loaded, Menu.GANG_STATS, "guis/gangs.yml", "stats-title");
        loadTitleKey(loaded, Menu.GANG_CONTRIBUTIONS, "guis/gangs.yml", "contributions-title");
        loadTitleKey(loaded, Menu.GANG_BOOSTERS, "guis/gangs.yml", "boosters-title");
        loadTitleKey(loaded, Menu.GANG_MISSIONS, "guis/gangs.yml", "missions-title");
        loadTitleKey(loaded, Menu.GANG_LEADERBOARDS, "guis/gangs.yml", "leaderboards-title");
        loadTitleKey(loaded, Menu.GANG_SETTINGS, "guis/gangs.yml", "settings-title");
        loadTitleKey(loaded, Menu.GANG_AUDIT, "guis/gangs.yml", "audit-title");
        loadTitle(loaded, Menu.ADMIN, "guis/admin.yml");
        loadTitleKey(loaded, Menu.ADMIN_MINES, "guis/admin.yml", "submenus.mine-manager");
        loadTitleKey(loaded, Menu.ADMIN_COMPOSITION, "guis/admin.yml", "submenus.composition-editor");
        loadTitleKey(loaded, Menu.ADMIN_RESET, "guis/admin.yml", "submenus.reset-settings-editor");
        loadTitleKey(loaded, Menu.ADMIN_RANKS, "guis/admin.yml", "submenus.rank-editor");
        loadTitleKey(loaded, Menu.ADMIN_PRESTIGES, "guis/admin.yml", "submenus.prestige-editor");
        loadTitleKey(loaded, Menu.ADMIN_SELL, "guis/admin.yml", "submenus.sell-price-editor");
        loadTitleKey(loaded, Menu.ADMIN_BOOSTERS, "guis/admin.yml", "submenus.booster-manager");
        loadTitleKey(loaded, Menu.ADMIN_BLOCK_EVENTS, "guis/admin.yml", "submenus.block-event-editor");
        loadTitleKey(loaded, Menu.ADMIN_INTEGRATIONS, "guis/admin.yml", "submenus.integration-status");
        loadTitleKey(loaded, Menu.ADMIN_DIAGNOSTICS, "guis/admin.yml", "submenus.diagnostics");
        loadTitleKey(loaded, Menu.ADMIN_PLAYERS, "guis/admin.yml", "submenus.player-progression-manager");
        GuiThemeConfig loadedTheme = GuiThemeConfig.load(new java.io.File(plugin.getDataFolder(), "guis/theme.yml"));
        return new Presentation(loaded, loadedTheme);
    }

    public Presentation snapshot() { return new Presentation(titles, theme); }
    public void apply(Presentation next) {
        titles = next.titles();
        theme = next.theme();
        generation.incrementAndGet();
        sessions.clear();
        confirmations.clear();
        dangerConfirmations.clear();
        adminInputs.clear();
        gangRankInputs.clear();
    }

    public NamespacedKey actionKey() { return actionKey; }
    public NamespacedKey sessionKey() { return sessionKey; }
    public GuiThemeConfig theme() { return theme; }
    public void clear(UUID playerId) {
        sessions.remove(playerId);
        lastClicks.remove(playerId);
        adminInputs.clear(playerId);
        gangRankInputs.remove(playerId);
        confirmations.entrySet().removeIf(entry -> entry.getValue().playerId().equals(playerId));
        dangerConfirmations.entrySet().removeIf(entry -> entry.getValue().playerId().equals(playerId));
    }

    private void loadTitle(Map<Menu, String> output, Menu menu, String path) throws Exception {
        loadTitleKey(output, menu, path, "title");
    }

    private void loadTitleKey(Map<Menu, String> output, Menu menu, String path, String key) throws Exception {
        java.io.File file = new java.io.File(plugin.getDataFolder(), path);
        if (!file.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.load(file);
        String title = yaml.getString(key);
        if (title != null && !title.isBlank()) output.put(menu, title);
    }

    public void open(Player player, Menu menu) { open(player, menu, 0, ""); }
    public void open(Player player, Menu menu, int page, String context) {
        switch (menu) {
            case MAIN -> openMain(player);
            case MINES -> openMines(player, page);
            case PROGRESSION -> openProgression(player, page);
            case RANKUP_CONFIRM -> openRankupConfirm(player, false);
            case RANKUP_MAX_CONFIRM -> openRankupConfirm(player, true);
            case PRESTIGE -> openPrestige(player, page);
            case PRESTIGE_CONFIRM -> openPrestigeConfirm(player);
            case DANGER_CONFIRM -> openDangerConfirmation(player, context);
            case SELLING -> openSelling(player);
            case BOOSTERS -> openBoosters(player);
            case STATISTICS -> openStatistics(player);
            case LEADERBOARD -> openLeaderboard(player, context.isBlank() ? "blocks:lifetime" : context);
            case GANG -> openGang(player);
            case GANG_MEMBERS -> openGangMembers(player, page);
            case GANG_MEMBER -> openGangMember(player, context);
            case GANG_RANKS -> openGangRanks(player, page);
            case GANG_RANK_EDIT -> openGangRankEdit(player, context);
            case GANG_RANK_PERMISSIONS -> openGangRankPermissions(player, context);
            case GANG_UPGRADES -> openGangUpgrades(player, page);
            case GANG_BANK -> openGangBank(player);
            case GANG_STATS -> openGangStats(player);
            case GANG_CONTRIBUTIONS -> openGangContributions(player, page);
            case GANG_BOOSTERS -> openGangBoosters(player, page);
            case GANG_MISSIONS -> openGangMissions(player, page);
            case GANG_LEADERBOARDS -> openGangLeaderboards(player, page);
            case GANG_SETTINGS -> openGangSettings(player);
            case GANG_AUDIT -> openGangAudit(player, page);
            case ADMIN -> openAdmin(player);
            default -> openAdminCategory(player, menu);
        }
    }

    private GuiSession session(Player player, Menu menu, int page, String context) {
        GuiSession session = new GuiSession(UUID.randomUUID(), menu, page, context, generation.get(),
                System.currentTimeMillis());
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    private Inventory create(Player player, GuiSession session, int size) {
        Holder holder = new Holder(session.menu(), session.page(), session.context(), session.id(), session.generation());
        Inventory inventory = Bukkit.createInventory(holder, size,
                ColorUtil.component(titles.getOrDefault(session.menu(), "&5RelicPrison")));
        holder.inventory(inventory);
        return inventory;
    }

    private void openMain(Player player) {
        GuiSession session = session(player, Menu.MAIN, 0, "");
        Inventory inv = create(player, session, 27);
        fill(inv, session);
        PlayerProfile profile = profile(player);
        inv.setItem(4, item(session, Material.COMPASS, "&b&lPrison Hub",
                List.of("&7Choose a section below.", "", "&7Rank: &f" + (profile == null ? "Loading" : profile.currentRank()),
                        "&7Prestige: &d" + (profile == null ? "Loading" : profile.currentPrestige())), "none"));
        inv.setItem(10, item(session, Material.DIAMOND_PICKAXE, "&a&lMines",
                List.of("&7Browse the prison mine directory.", "", "&7Locked mines are clearly marked.",
                        "&8Command: /mine"), "menu:mines"));
        inv.setItem(11, item(session, Material.EXPERIENCE_BOTTLE, "&b&lRank Progression",
                List.of("&7Review ranks, prices, and unlocks.", "", "&8Commands: /rankup, /rankupmax"),
                "menu:progression"));
        inv.setItem(12, item(session, Material.NETHER_STAR, "&d&lPrestige",
                List.of("&7Advance into permanent prestige tiers.", "", "&8Command: /prestige"), "menu:prestige"));
        inv.setItem(14, item(session, Material.GOLD_INGOT, "&6&lSelling",
                List.of("&7Sell inventory or hand items.", "", "&8Commands: /sellall, /sellhand, /sellvalue"),
                "menu:selling"));
        inv.setItem(15, item(session, Material.BLAZE_POWDER, "&d&lBoosters",
                List.of("&7Inspect active personal and server boosts.", "", "&8Command: /booster status"),
                "menu:boosters"));
        inv.setItem(16, item(session, Material.WRITABLE_BOOK, "&b&lStatistics",
                List.of("&7Track progress and compare rankings.", "", "&8Commands: /stats, /leaderboard"),
                "menu:statistics"));
        inv.setItem(13, item(session, Material.SHIELD, "&d&lGang",
                List.of("&7Open your gang headquarters.", "", "&8Command: /gang"), "menu:gang"));
        if (AdminEditorAccessPolicy.allowed(player::hasPermission)) {
            inv.setItem(22, item(session, Material.COMMAND_BLOCK, "&c&lAdministration",
                    List.of("&7Open protected management tools.", "", "&cStaff access only.",
                            "&8Command: /rp admin"), "menu:admin"));
        }
        player.openInventory(inv);
    }

    private void openMines(Player player, int requestedPage) {
        List<MineDefinition> mines = plugin.mineService().mines().stream()
                .sorted(Comparator.comparingInt(MineDefinition::sortOrder).thenComparing(MineDefinition::id)).toList();
        int pages = Math.max(1, (mines.size() + 35) / 36);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        GuiSession session = session(player, Menu.MINES, page, "");
        Inventory inv = create(player, session, 54);
        fill(inv, session);
        MineDefinition currentMine = mines.stream()
                .filter(mine -> plugin.mineAccess().canEnter(player.getUniqueId(), mine.id()))
                .max(Comparator.comparingInt(MineDefinition::sortOrder)).orElse(null);
        int start = page * 36;
        for (int index = 0; index < 36 && start + index < mines.size(); index++) {
            MineDefinition mine = mines.get(start + index);
            boolean access = plugin.mineAccess().canEnter(player.getUniqueId(), mine.id());
            boolean current = currentMine != null && currentMine.id().equals(mine.id());
            long remaining = plugin.mineResets().remainingBlocks(mine.id());
            long nextReset = plugin.mineResets().nextReset(mine.id());
            long resetSeconds = nextReset <= 0 ? -1 : Math.max(0L, (nextReset - System.currentTimeMillis()) / 1000L);
            long remainingPercent = mine.volume() <= 0 ? 0 : Math.max(0L,
                    Math.min(100L, Math.round(remaining * 100.0D / mine.volume())));
            List<String> lore = new ArrayList<>();
            lore.add(access ? "&7Warp to this prison mining area." : "&7This mine has not been unlocked.");
            lore.add("");
            lore.add("&7Required Rank: &f" + (mine.requiredRank() == null ? "None" : mine.requiredRank().toUpperCase(Locale.ROOT)));
            lore.add("&7Reset: &b" + (resetSeconds < 0 ? "Manual" : formatDuration(resetSeconds)));
            lore.add("&7Blocks Remaining: &b" + remainingPercent + "% &8(" + plugin.numbers().full(Math.max(0, remaining)) + ")");
            lore.add("");
            lore.add(current ? "&bCurrent Highest Mine" : access ? "&aUnlocked" : "&cLocked");
            lore.add(access ? "&eClick to warp." : "&eUse /rankup to progress.");
            GuiVisuals.State state = current ? GuiVisuals.State.CURRENT
                    : access ? GuiVisuals.State.CLICKABLE : GuiVisuals.State.LOCKED;
            String name = (current ? "&b&l" : access ? "&a&l" : "&c&l") + mine.displayName()
                    + (access ? "" : " &8[LOCKED]");
            inv.setItem(index, item(session, GuiVisuals.mineMaterial(mine.id(), mine.displayName(), start + index, access),
                    name, lore, access ? "mine:" + mine.id() : "deny:This mine is still locked.", state));
        }
        controls(inv, session, page, pages, "menu:main", "page:mines:");
        player.openInventory(inv);
    }

    private void openGang(Player player) {
        Gang gang = plugin.gangs().cachedGang(player.getUniqueId()).orElse(null);
        GuiSession session = session(player, Menu.GANG, 0, "");
        Inventory inv = create(player, session, 54);
        fill(inv, session);
        if (gang == null) {
            inv.setItem(22, item(session, Material.BARRIER, "&cYou are not in a gang",
                    List.of("&7Use /gang create <name> <tag>", "&7Use /gang invites or /gang join <gang>"), "none"));
            inv.setItem(49, item(session, Material.ARROW, "&cBack", List.of(), "menu:main"));
            player.openInventory(inv);
            return;
        }
        inv.setItem(4, item(session, Material.SHIELD, gang.color() + "&l" + gang.name() + " &8[" + gang.tag() + "]",
                List.of("&7Your gang headquarters.", "", "&7Level: &b" + gang.level(), "&7XP: &b" + gang.xp(),
                        "&7Points: &6" + gang.points(), "&7Members: &f" + gang.memberCount() + "/" + gang.memberLimit(),
                        "&7Bank: &6" + plugin.numbers().currency(gang.bankBalance()), "", "&7MOTD: &f" + gang.motd()),
                "none", GuiVisuals.State.CURRENT));
        inv.setItem(10, gangSection(session, Material.PLAYER_HEAD, "Members", Menu.GANG_MEMBERS));
        inv.setItem(11, gangSection(session, Material.NAME_TAG, "Ranks", Menu.GANG_RANKS));
        inv.setItem(12, gangSection(session, Material.ANVIL, "Upgrades", Menu.GANG_UPGRADES));
        inv.setItem(13, gangSection(session, Material.GOLD_INGOT, "Gang Bank", Menu.GANG_BANK));
        inv.setItem(14, gangSection(session, Material.BOOK, "Statistics", Menu.GANG_STATS));
        inv.setItem(15, gangSection(session, Material.DIAMOND_PICKAXE, "Contributions", Menu.GANG_CONTRIBUTIONS));
        inv.setItem(16, gangSection(session, Material.BLAZE_POWDER, "Boosters", Menu.GANG_BOOSTERS));
        inv.setItem(20, gangSection(session, Material.WRITABLE_BOOK, "Missions", Menu.GANG_MISSIONS));
        inv.setItem(21, gangSection(session, Material.NETHER_STAR, "Leaderboards", Menu.GANG_LEADERBOARDS));
        inv.setItem(22, gangSection(session, Material.COMPARATOR, "Settings", Menu.GANG_SETTINGS));
        inv.setItem(23, gangSection(session, Material.BOOK, "Audit Log", Menu.GANG_AUDIT));
        inv.setItem(49, item(session, Material.ARROW, "&cBack", List.of(), "menu:main"));
        player.openInventory(inv);
    }

    private ItemStack gangSection(GuiSession session, Material material, String title, Menu menu) {
        String color = title.contains("Bank") ? "&6" : title.equals("Statistics") ? "&b" : "&d";
        return item(session, material, color + "&l" + title,
                List.of("&7Open " + title.toLowerCase(Locale.ROOT) + ".", "", "&eClick to open."),
                "menu:" + menu.name().toLowerCase(Locale.ROOT));
    }

    private void openGangMembers(Player player, int requestedPage) {
        plugin.gangs().members(player.getUniqueId()).whenComplete((members, error) -> sync(() -> {
            if (error != null) { player.sendMessage(ColorUtil.color("&c" + rootMessage(error))); return; }
            int pages = pages(members.size(), 36);
            int page = boundedPage(requestedPage, pages);
            GuiSession session = session(player, Menu.GANG_MEMBERS, page, "");
            Inventory inv = create(player, session, 54);
            fill(inv, session);
            int slot = 0;
            for (GangMember member : page(members, page, 36)) {
                String name = Bukkit.getOfflinePlayer(member.playerId()).getName();
                boolean online = Bukkit.getPlayer(member.playerId()) != null;
                inv.setItem(slot++, playerHead(session, member.playerId(), (online ? "&a&l" : "&7&l")
                                + (name == null ? member.playerId() : name),
                        List.of("&7Gang member profile.", "", "&7Status: " + (online ? "&aOnline" : "&7Offline"),
                                "&7Joined: &f" + member.joinedAt(), "", "&eClick to inspect and manage."),
                        "gangmember:" + member.playerId(), online ? GuiVisuals.State.ENABLED : GuiVisuals.State.STATUS));
            }
            controls(inv, session, page, pages, "menu:gang", "page:gang_members:");
            player.openInventory(inv);
        }));
    }

    private void openGangMember(Player player, String context) {
        UUID target;
        try { target = UUID.fromString(context); }
        catch (IllegalArgumentException error) { open(player, Menu.GANG_MEMBERS); return; }
        String name = Bukkit.getOfflinePlayer(target).getName();
        GuiSession session = session(player, Menu.GANG_MEMBER, 0, context);
        Inventory inv = create(player, session, 27);
        fill(inv, session);
        String argument = name == null ? target.toString() : name;
        boolean online = Bukkit.getPlayer(target) != null;
        inv.setItem(4, playerHead(session, target, (online ? "&a&l" : "&7&l") + argument,
                List.of("&7Manage this gang member.", "", "&7Status: " + (online ? "&aOnline" : "&7Offline"),
                        "&7UUID: &8" + target), "none", online ? GuiVisuals.State.ENABLED : GuiVisuals.State.STATUS));
        inv.setItem(10, item(session, Material.LIME_DYE, "&a&lPromote", List.of("&7Move this member up one rank.", "",
                        "&8Permission and hierarchy are rechecked."),
                "gangcmd:promote:" + argument));
        inv.setItem(11, item(session, Material.ORANGE_DYE, "&e&lDemote", List.of("&7Move this member down one rank.", "",
                        "&8Permission and hierarchy are rechecked."),
                "gangcmd:demote:" + argument));
        inv.setItem(13, item(session, Material.IRON_BOOTS, "&c&lKick Member", List.of("&7Remove this member from the gang.", "",
                        "&cDangerous action."),
                "gangcmd:kick:" + argument));
        inv.setItem(15, item(session, Material.NETHER_STAR, "&c&lTransfer Ownership",
                List.of("&7Make this member the new owner.", "", "&cOnly the owner can transfer.",
                        "&cA confirmation is required."), "gangcmd:transfer:" + argument));
        inv.setItem(22, item(session, Material.ARROW, "&cBack", List.of(), "menu:gang_members"));
        player.openInventory(inv);
    }

    private void openGangRanks(Player player, int requestedPage) {
        plugin.gangs().ranks(player.getUniqueId()).whenComplete((values, error) -> sync(() -> {
            if (error != null) { player.sendMessage(ColorUtil.color("&c" + rootMessage(error))); return; }
            int pages = pages(values.size(), 36);
            int page = boundedPage(requestedPage, pages);
            GuiSession session = session(player, Menu.GANG_RANKS, page, "");
            Inventory inv = create(player, session, 54);
            fill(inv, session);
            int slot = 0;
            for (GangRank rank : page(values, page, 36)) inv.setItem(slot++, item(session,
                    rank.owner() ? Material.NETHER_STAR : rank.defaultRank() ? Material.NAME_TAG : Material.WRITABLE_BOOK,
                    rank.color() + "&l" + rank.displayName(), List.of("&7Gang rank and permission tier.", "",
                            "&7Priority: &f" + rank.priority(), "&7Permissions: &f" + rank.permissions().size(), "",
                            rank.owner() ? "&bOwner Rank" : rank.defaultRank() ? "&8Default Rank" : "&aCustom Rank"),
                    "none", rank.owner() ? GuiVisuals.State.CURRENT : GuiVisuals.State.STATUS));
            if (plugin.gangs().hasPermission(player.getUniqueId(),
                    site.mcrelicworld.relicprison.gang.GangPermission.MANAGE_RANKS)) {
                slot = 0;
                for (GangRank rank : page(values, page, 36)) inv.setItem(slot++, item(session,
                        rank.owner() ? Material.NETHER_STAR : rank.defaultRank() ? Material.NAME_TAG : Material.WRITABLE_BOOK,
                        rank.color() + "&l" + rank.displayName(), List.of("&7Edit this gang rank.", "",
                                "&7Priority: &f" + rank.priority(), "&7Permissions: &f" + rank.permissions().size(), "",
                                rank.defaultRank() ? "&8Default Rank" : "&aCustom Rank", "&eClick to edit."),
                        "gangrank:edit:" + rank.id()));
                inv.setItem(46, item(session, Material.LIME_DYE, "&a&lCreate Custom Rank",
                        List.of("&7Add a new permission tier.", "", "&eClick, then enter name, priority, and color."),
                        "gangrank:create"));
            }
            controls(inv, session, page, pages, "menu:gang", "page:gang_ranks:");
            player.openInventory(inv);
        }));
    }

    private void openGangRankEdit(Player player, String context) {
        UUID rankId;
        try { rankId = UUID.fromString(context); }
        catch (IllegalArgumentException error) { open(player, Menu.GANG_RANKS); return; }
        plugin.gangs().repository().rank(rankId).whenComplete((loaded, error) -> sync(() -> {
            if (error != null || loaded.isEmpty()) { player.sendMessage(ColorUtil.color("&cRank not found")); return; }
            GangRank rank = loaded.get();
            GuiSession session = session(player, Menu.GANG_RANK_EDIT, 0, context);
            Inventory inv = create(player, session, 27);
            fill(inv, session);
            inv.setItem(4, item(session, rank.owner() ? Material.NETHER_STAR : Material.NAME_TAG,
                    rank.color() + "&l" + rank.displayName(), List.of("&7Rank editor overview.", "",
                            "&7Priority: &f" + rank.priority(), "&7ID: &8" + rank.id(), "",
                            rank.owner() ? "&bProtected Owner Rank" : rank.defaultRank() ? "&8Default Rank" : "&aCustom Rank"),
                    "none", rank.owner() ? GuiVisuals.State.CURRENT : GuiVisuals.State.STATUS));
            inv.setItem(10, item(session, Material.NAME_TAG, "&e&lRename Rank",
                    List.of("&7Change the visible rank name.", "", "&eEnter the new name in chat."),
                    "gangrank:field:name:" + rank.id()));
            inv.setItem(11, item(session, Material.COMPARATOR, "&e&lChange Priority",
                    List.of("&7Move the rank within the hierarchy.", "", "&eEnter a numeric priority in chat."),
                    "gangrank:field:priority:" + rank.id()));
            inv.setItem(12, item(session, Material.CYAN_DYE, "&e&lChange Color",
                    List.of("&7Set the rank's display color.", "", "&eEnter a legacy color code in chat."),
                    "gangrank:field:color:" + rank.id()));
            inv.setItem(14, item(session, Material.WRITABLE_BOOK, "&e&lEdit Permissions",
                    List.of("&7Configure what this rank can manage."),
                    "gangrank:permissions:" + rank.id()));
            if (!rank.defaultRank()) inv.setItem(16, item(session, theme.deleteMaterial(), "&c&lDelete Custom Rank",
                    List.of("&7Remove this custom rank.", "", "&7Affected members move to Recruit.",
                            "&cA confirmation is required."), "gangrank:delete:" + rank.id()));
            inv.setItem(22, item(session, Material.ARROW, "&cBack", List.of(), "menu:gang_ranks"));
            player.openInventory(inv);
        }));
    }

    private void openGangRankPermissions(Player player, String context) {
        UUID rankId;
        try { rankId = UUID.fromString(context); }
        catch (IllegalArgumentException error) { open(player, Menu.GANG_RANKS); return; }
        plugin.gangs().repository().rank(rankId).whenComplete((loaded, error) -> sync(() -> {
            if (error != null || loaded.isEmpty()) { player.sendMessage(ColorUtil.color("&cRank not found")); return; }
            GangRank rank = loaded.get();
            GuiSession session = session(player, Menu.GANG_RANK_PERMISSIONS, 0, context);
            Inventory inv = create(player, session, 54);
            fill(inv, session);
            int slot = 0;
            for (site.mcrelicworld.relicprison.gang.GangPermission permission
                    : site.mcrelicworld.relicprison.gang.GangPermission.values()) {
                boolean enabled = rank.permissions().contains(permission);
                boolean protectedOwner = rank.owner();
                inv.setItem(slot++, item(session, enabled ? Material.LIME_WOOL : Material.RED_WOOL,
                        (enabled ? "&a&l" : "&c&l") + permission.key().replace('_', ' '),
                        List.of("&7Internal gang rank permission.", "", "&7State: "
                                        + (enabled ? "&aEnabled" : "&cDisabled"), "",
                                protectedOwner ? "&bProtected for the Owner rank."
                                        : "&eClick to " + (enabled ? "disable." : "enable.")),
                        protectedOwner ? "deny:Owner permissions cannot be removed."
                                : "gangrank:permission:" + rank.id() + ':' + permission.key() + ':' + !enabled,
                        enabled ? GuiVisuals.State.ENABLED : GuiVisuals.State.DISABLED));
            }
            inv.setItem(49, item(session, Material.ARROW, "&cBack", List.of(), "gangrank:edit:" + rank.id()));
            player.openInventory(inv);
        }));
    }

    private void openGangUpgrades(Player player, int requestedPage) {
        List<site.mcrelicworld.relicprison.gang.GangConfig.UpgradeDefinition> values =
                new ArrayList<>(plugin.gangs().config().upgrades().values());
        int pages = pages(values.size(), 36);
        int page = boundedPage(requestedPage, pages);
        GuiSession session = session(player, Menu.GANG_UPGRADES, page, "");
        Inventory inv = create(player, session, 54);
        fill(inv, session);
        int slot = 0;
        Gang gang = plugin.gangs().cachedGang(player.getUniqueId()).orElse(null);
        Map<String, Integer> purchased = plugin.gangs().cachedUpgrades(player.getUniqueId());
        for (var upgrade : page(values, page, 36)) {
            int tier = plugin.gangs().cachedUpgradeTier(player.getUniqueId(), upgrade.id());
            boolean maximum = tier >= upgrade.maxTier();
            var next = maximum ? null : upgrade.tier(tier + 1);
            boolean prerequisitesMet = upgrade.prerequisites().stream()
                    .allMatch(required -> purchased.getOrDefault(required.upgradeId(), 0) >= required.tier());
            boolean affordable = maximum || gang != null && (upgrade.costType()
                    == site.mcrelicworld.relicprison.gang.GangConfig.CostType.POINTS
                    ? java.math.BigDecimal.valueOf(gang.points()).compareTo(next.cost()) >= 0
                    : gang.bankBalance().compareTo(next.cost()) >= 0);
            GuiVisuals.State state = GuiVisuals.upgradeState(maximum, prerequisitesMet, affordable);
            Material icon = upgrade.id().contains("member") ? Material.PLAYER_HEAD
                    : upgrade.id().contains("sell") ? Material.GOLD_INGOT
                    : upgrade.id().contains("mining") ? Material.DIAMOND_PICKAXE
                    : upgrade.id().contains("bank") ? Material.GOLD_BLOCK
                    : upgrade.id().contains("booster") ? Material.CLOCK : Material.ANVIL;
            inv.setItem(slot++, item(session, maximum ? Material.NETHER_STAR : icon,
                    (maximum ? "&b&l" : prerequisitesMet && affordable ? "&a&l" : "&c&l") + upgrade.displayName(),
                    List.of("&7Improve a permanent gang benefit.", "", "&7Current Tier: &b" + tier + "&8/&f"
                                    + upgrade.maxTier(), "&7Next Cost: " + (maximum ? "&bMAXIMUM"
                                    : "&6" + next.cost() + " &f" + upgrade.costType()), "",
                            maximum ? "&bMaximum Tier Reached" : !prerequisitesMet ? "&cPrerequisite Missing"
                                    : affordable ? "&aAffordable — click to purchase." : "&cInsufficient Funds"),
                    maximum || !prerequisitesMet || !affordable ? "none" : "gangcmd:upgrade:" + upgrade.id(), state));
        }
        controls(inv, session, page, pages, "menu:gang", "page:gang_upgrades:");
        player.openInventory(inv);
    }

    private void openGangBank(Player player) {
        Gang gang = plugin.gangs().cachedGang(player.getUniqueId()).orElse(null);
        if (gang == null) { open(player, Menu.GANG); return; }
        plugin.gangs().repository().bankHistory(gang.id(), 20).whenComplete((history, error) -> sync(() -> {
            if (error != null) { player.sendMessage(ColorUtil.color("&c" + rootMessage(error))); return; }
            GuiSession session = session(player, Menu.GANG_BANK, 0, "");
            Inventory inv = create(player, session, 54);
            fill(inv, session);
            inv.setItem(4, item(session, Material.GOLD_BLOCK, "&6&lGang Bank",
                    List.of("&7Shared gang treasury and transaction ledger.", "", "&7Balance: &6"
                                    + plugin.numbers().currency(gang.bankBalance()), "&7Capacity: &f"
                                    + plugin.numbers().currency(plugin.gangs().effectiveBankCapacity(player.getUniqueId())), "",
                            "&8/gang bank deposit <amount>", "&8/gang bank withdraw <amount>"),
                    "none", GuiVisuals.State.STATUS));
            int slot = 9;
            for (GangBankTransaction entry : history.stream().limit(36).toList()) inv.setItem(slot++, item(session,
                    entry.amount().signum() >= 0 ? Material.EMERALD : Material.REDSTONE,
                    (entry.amount().signum() >= 0 ? "&a&l" : "&c&l") + entry.type().name().replace('_', ' '),
                    List.of("&7Recorded bank transaction.", "", "&7Amount: "
                                    + (entry.amount().signum() >= 0 ? "&a+" : "&c") + entry.amount(),
                            "&7Previous Balance: &f" + entry.previousBalance(), "&7New Balance: &6"
                                    + entry.newBalance(), "&7Reason: &f" + entry.reason(), "&7Time: &8"
                                    + entry.createdAt()), "none", GuiVisuals.State.STATUS));
            inv.setItem(49, item(session, Material.ARROW, "&cBack", List.of(), "menu:gang"));
            player.openInventory(inv);
        }));
    }

    private void openGangStats(Player player) {
        plugin.gangs().statistics(player.getUniqueId()).whenComplete((stats, error) -> sync(() -> {
            if (error != null || stats == null) { player.sendMessage(ColorUtil.color("&c" + (error == null
                    ? "You are not in a gang" : rootMessage(error)))); return; }
            GuiSession session = session(player, Menu.GANG_STATS, 0, "");
            Inventory inv = create(player, session, 27);
            fill(inv, session);
            putGangStat(inv, session, 10, Material.DIAMOND_PICKAXE, "Blocks", stats.blocks());
            putGangStat(inv, session, 11, Material.GOLD_INGOT, "Money", stats.money());
            putGangStat(inv, session, 12, Material.EXPERIENCE_BOTTLE, "Gang XP", stats.gangXp());
            putGangStat(inv, session, 14, Material.EMERALD, "Rankups", stats.rankups());
            putGangStat(inv, session, 15, Material.NETHER_STAR, "Prestiges", stats.prestiges());
            putGangStat(inv, session, 16, Material.TARGET, "Block Events", stats.blockEvents());
            inv.setItem(22, item(session, Material.ARROW, "&cBack", List.of(), "menu:gang"));
            player.openInventory(inv);
        }));
    }

    private void putGangStat(Inventory inventory, GuiSession session, int slot, Material material,
                             String label, Object value) {
        String color = label.equals("Money") ? "&6" : label.equals("Prestiges") ? "&d" : "&b";
        inventory.setItem(slot, item(session, material, color + "&l" + label,
                List.of("&7Committed gang-wide statistic.", "", "&7Total: &f" + value),
                "none", GuiVisuals.State.STATUS));
    }

    private void openGangContributions(Player player, int requestedPage) {
        plugin.gangs().contributions(player.getUniqueId()).whenComplete((values, error) -> sync(() -> {
            if (error != null) { player.sendMessage(ColorUtil.color("&c" + rootMessage(error))); return; }
            int pages = pages(values.size(), 36);
            int page = boundedPage(requestedPage, pages);
            GuiSession session = session(player, Menu.GANG_CONTRIBUTIONS, page, "");
            Inventory inv = create(player, session, 54);
            fill(inv, session);
            int slot = 0;
            for (GangMemberStatistics stats : page(values, page, 36)) {
                String name = Bukkit.getOfflinePlayer(stats.playerId()).getName();
                boolean self = stats.playerId().equals(player.getUniqueId());
                inv.setItem(slot++, playerHead(session, stats.playerId(), (self ? "&b&l" : "&f&l")
                                + (name == null ? stats.playerId() : name),
                        List.of("&7Committed member contribution totals.", "", "&7Blocks: &f" + stats.blocks(),
                                "&7Money: &6" + stats.money(), "&7Gang XP: &b" + stats.gangXp(),
                                "&7Rankups: &f" + stats.rankups(), "&7Prestiges: &d" + stats.prestiges(),
                                "&7Block Events: &f" + stats.blockEvents(), "", self ? "&bYour Contributions"
                                        : "&8Gang Member"), "none", self ? GuiVisuals.State.CURRENT : GuiVisuals.State.STATUS));
            }
            controls(inv, session, page, pages, "menu:gang", "page:gang_contributions:");
            player.openInventory(inv);
        }));
    }

    private void openGangBoosters(Player player, int requestedPage) {
        List<GangBooster> values = plugin.gangs().boosters(player.getUniqueId());
        int pages = pages(values.size(), 36);
        int page = boundedPage(requestedPage, pages);
        GuiSession session = session(player, Menu.GANG_BOOSTERS, page, "");
        Inventory inv = create(player, session, 54);
        fill(inv, session);
        int slot = 0;
        long now = System.currentTimeMillis();
        for (GangBooster booster : page(values, page, 36)) {
            boolean active = booster.active(now);
            boolean scheduled = booster.enabled() && booster.startsAt() > now;
            Material icon = active ? GuiVisuals.boosterMaterial(booster.type().name())
                    : scheduled ? Material.CLOCK : Material.GRAY_DYE;
            String state = active ? "&dActive" : scheduled ? "&eScheduled" : "&7Expired / Disabled";
            inv.setItem(slot++, item(session, icon, (active ? "&d&l" : scheduled ? "&e&l" : "&7&l")
                            + booster.type().name().replace('_', ' ') + " &fx" + booster.multiplier(),
                    List.of("&7Gang-scoped progression multiplier.", "", "&7Starts: &f" + booster.startsAt(),
                            "&7Expires: &f" + booster.expiresAt(), "&7Source: &f" + booster.source(), "", state),
                    "none", active ? GuiVisuals.State.ACTIVE
                            : scheduled ? GuiVisuals.State.CLICKABLE : GuiVisuals.State.DISABLED));
        }
        if (values.isEmpty()) inv.setItem(22, item(session, Material.GLASS_BOTTLE, "&8&lNo Gang Boosters",
                List.of("&7No active or scheduled gang boosters."), "none", GuiVisuals.State.UNAVAILABLE));
        controls(inv, session, page, pages, "menu:gang", "page:gang_boosters:");
        player.openInventory(inv);
    }

    private void openGangMissions(Player player, int requestedPage) {
        plugin.gangs().missions(player.getUniqueId()).whenComplete((values, error) -> sync(() -> {
            if (error != null) { player.sendMessage(ColorUtil.color("&c" + rootMessage(error))); return; }
            int pages = pages(values.size(), 36);
            int page = boundedPage(requestedPage, pages);
            GuiSession session = session(player, Menu.GANG_MISSIONS, page, "");
            Inventory inv = create(player, session, 54);
            fill(inv, session);
            int slot = 0;
            for (GangMissionState mission : page(values, page, 36)) {
                boolean completed = mission.state() == GangMissionState.State.COMPLETED;
                boolean claimed = mission.state() == GangMissionState.State.CLAIMED;
                Material icon = GuiVisuals.missionMaterial(mission.state().name());
                GuiVisuals.State state = completed ? GuiVisuals.State.COMPLETED
                        : claimed ? GuiVisuals.State.STATUS : GuiVisuals.State.CLICKABLE;
                inv.setItem(slot++, item(session, icon, (completed ? "&a&l" : claimed ? "&b&l" : "&e&l")
                                + mission.missionId().replace('_', ' '),
                        List.of("&7Work together toward this objective.", "", "&7Progress: &f" + mission.progress()
                                        + "&8/&f" + mission.target(), "&7Period: &f" + mission.periodKey(), "",
                                completed ? "&aCompleted — reward available" : claimed ? "&bReward Claimed"
                                        : "&eMission In Progress"),
                        completed ? "gangcmd:missions:claim:" + mission.missionId() : "none", state));
            }
            controls(inv, session, page, pages, "menu:gang", "page:gang_missions:");
            player.openInventory(inv);
        }));
    }

    private void openGangLeaderboards(Player player, int requestedPage) {
        plugin.gangs().leaderboard("blocks", 1000).whenComplete((values, error) -> sync(() -> {
            if (error != null) { player.sendMessage(ColorUtil.color("&c" + rootMessage(error))); return; }
            int pages = pages(values.size(), 36);
            int page = boundedPage(requestedPage, pages);
            GuiSession session = session(player, Menu.GANG_LEADERBOARDS, page, "");
            Inventory inv = create(player, session, 54);
            fill(inv, session);
            int slot = 0;
            Gang gang = plugin.gangs().cachedGang(player.getUniqueId()).orElse(null);
            for (GangLeaderboardEntry entry : page(values, page, 36)) {
                boolean own = gang != null && gang.id().equals(entry.gangId());
                Material icon = entry.position() == 1 ? Material.NETHER_STAR : entry.position() == 2
                        ? Material.DIAMOND_BLOCK : entry.position() == 3 ? Material.GOLD_BLOCK : Material.SHIELD;
                inv.setItem(slot++, item(session, icon, (own ? "&b&l" : entry.position() <= 3 ? "&6&l" : "&f&l")
                                + "#" + entry.position() + " " + entry.gangName(),
                        List.of("&7Gang blocks leaderboard.", "", "&7Blocks: &f" + entry.value(),
                                "&7Position: &b#" + entry.position(), "", own ? "&bYour Gang" : "&8Ranked Gang"),
                        "none", own ? GuiVisuals.State.CURRENT : GuiVisuals.State.STATUS));
            }
            controls(inv, session, page, pages, "menu:gang", "page:gang_leaderboards:");
            player.openInventory(inv);
        }));
    }

    private void openGangSettings(Player player) {
        Gang gang = plugin.gangs().cachedGang(player.getUniqueId()).orElse(null);
        if (gang == null) { open(player, Menu.GANG); return; }
        GuiSession session = session(player, Menu.GANG_SETTINGS, 0, "");
        Inventory inv = create(player, session, 27);
        fill(inv, session);
        inv.setItem(10, item(session, gang.joinMode() == site.mcrelicworld.relicprison.gang.GangJoinMode.OPEN
                        ? Material.OAK_DOOR : gang.joinMode() == site.mcrelicworld.relicprison.gang.GangJoinMode.CLOSED
                        ? Material.IRON_DOOR : Material.TRIPWIRE_HOOK,
                "&e&lPrivacy &8• &f" + gang.joinMode(), List.of("&7Control how players join the gang.", "",
                        "&7Current Mode: &b" + gang.joinMode(), "&8/gang settings privacy <mode>"), "none"));
        inv.setItem(12, item(session, Material.NAME_TAG, "&e&lGang Identity",
                List.of("&7Public gang identity and appearance.", "", "&7Name: &f" + gang.name(),
                        "&7Tag: &f" + gang.tag(), "&7Color: &f" + gang.color(), "&7Description: &f"
                                + gang.description()), "none"));
        inv.setItem(14, item(session, gang.home() == null ? Material.RECOVERY_COMPASS : Material.COMPASS,
                "&e&lGang Home", List.of("&7Shared gang teleport location.", "",
                        gang.home() == null ? "&cNo home set." : "&aSet in &f" + gang.home().world(),
                        "&8/gang sethome • /gang home"), "none",
                gang.home() == null ? GuiVisuals.State.DISABLED : GuiVisuals.State.ENABLED));
        inv.setItem(16, item(session, Material.WRITABLE_BOOK, "&e&lMessage of the Day",
                List.of("&7Message shown to gang members.", "", "&f" + gang.motd()), "none"));
        inv.setItem(20, item(session, theme.deleteMaterial(), "&c&lDisband Gang",
                List.of("&7Permanently disable this gang.", "", "&cMembers, invites, missions, and boosters end.",
                        "&cHistorical audit and season records remain.", "", "&cA confirmation is required."),
                "gangdanger:disband", GuiVisuals.State.DANGEROUS));
        inv.setItem(22, item(session, Material.ARROW, "&cBack", List.of(), "menu:gang"));
        player.openInventory(inv);
    }

    private void openGangAudit(Player player, int requestedPage) {
        Gang gang = plugin.gangs().cachedGang(player.getUniqueId()).orElse(null);
        if (gang == null) { open(player, Menu.GANG); return; }
        plugin.gangs().repository().audit(gang.id(), 1000).whenComplete((values, error) -> sync(() -> {
            if (error != null) { player.sendMessage(ColorUtil.color("&c" + rootMessage(error))); return; }
            int pages = pages(values.size(), 36);
            int page = boundedPage(requestedPage, pages);
            GuiSession session = session(player, Menu.GANG_AUDIT, page, "");
            Inventory inv = create(player, session, 54);
            fill(inv, session);
            int slot = 0;
            for (GangAuditEntry entry : page(values, page, 36)) inv.setItem(slot++, item(session,
                    entry.success() ? Material.WRITABLE_BOOK : Material.REDSTONE,
                    (entry.success() ? "&b&l" : "&c&l") + entry.actionType().replace('_', ' '),
                    List.of("&7Immutable gang audit record.", "", "&7Actor: &f" + entry.actorId(),
                            "&7Target: &f" + entry.targetId(), "&7Reason: &f" + entry.reason(),
                            "&7Time: &8" + entry.createdAt(), "", entry.success() ? "&aSucceeded" : "&cFailed"),
                    "none", entry.success() ? GuiVisuals.State.STATUS : GuiVisuals.State.DISABLED));
            controls(inv, session, page, pages, "menu:gang", "page:gang_audit:");
            player.openInventory(inv);
        }));
    }

    private void sync(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run(); else Bukkit.getScheduler().runTask(plugin, action);
    }

    private void openProgression(Player player, int requestedPage) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        List<RankDefinition> ranks = plugin.rankService().definitions();
        int pages = Math.max(1, (ranks.size() + 35) / 36);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        GuiSession session = session(player, Menu.PROGRESSION, page, "");
        Inventory inv = create(player, session, 54);
        fill(inv, session);
        int current = plugin.rankService().indexOf(profile.currentRank());
        int start = page * 36;
        for (int index = 0; index < 36 && start + index < ranks.size(); index++) {
            RankDefinition rank = ranks.get(start + index);
            int absolute = start + index;
            boolean completed = absolute < current;
            boolean selected = absolute == current;
            boolean locked = absolute > current;
            String state = completed ? "&aCompleted" : selected ? "&bCurrent Rank" : "&cLocked";
            List<String> lore = new ArrayList<>();
            lore.add("&7Prison rank " + (absolute + 1) + " of " + ranks.size() + ".");
            lore.add("");
            lore.addAll(rank.lore());
            lore.add("&7Mine: &f" + (rank.mineId() == null ? "None" : rank.mineId()));
            if (absolute < ranks.size() - 1) lore.add("&7Next cost: &f"
                    + plugin.numbers().currency(rank.nextCost().multiply(plugin.prestigeService()
                    .rankCostMultiplier(profile.currentPrestige()))));
            lore.add("");
            lore.add(state);
            if (locked) lore.add("&eRank up to unlock this tier.");
            GuiVisuals.State visual = selected ? GuiVisuals.State.CURRENT
                    : locked ? GuiVisuals.State.LOCKED : GuiVisuals.State.STATUS;
            inv.setItem(index, item(session, GuiVisuals.rankMaterial(absolute, ranks.size(), locked),
                    (selected ? "&b&l" : completed ? "&a&l" : "&c&l") + rank.displayName()
                            + (locked ? " &8[LOCKED]" : ""), lore, "none", visual));
        }
        controls(inv, session, page, pages, "menu:main", "page:progression:");
        inv.setItem(46, item(session, Material.EMERALD, "&a&lRank Up",
                List.of("&7Purchase the next available rank.", "", "&8Command: /rankup"), "confirm:rankup"));
        inv.setItem(52, item(session, Material.EMERALD_BLOCK, "&a&lRank Up Max",
                List.of("&7Purchase the highest affordable rank.", "", "&8Command: /rankupmax"), "confirm:rankupmax"));
        player.openInventory(inv);
    }

    private void openRankupConfirm(Player player, boolean maximum) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        RankPlan plan = rankPlan(player, profile, currentBalance(player), maximum);
        if (plan == null) {
            player.sendMessage(ColorUtil.color("&cNo affordable rankup target is available."));
            open(player, Menu.PROGRESSION);
            return;
        }
        String id = UUID.randomUUID().toString();
        long expires = System.currentTimeMillis() + plugin.config().snapshot().guiSecurity().confirmationTimeoutMillis();
        confirmations.put(id, new ConfirmationContext(player.getUniqueId(), maximum ? Menu.RANKUP_MAX_CONFIRM
                : Menu.RANKUP_CONFIRM, profile.currentRank(), profile.currentPrestige(), plan.targetRank(),
                null, plan.cost(), expires, new AtomicBoolean()));
        GuiSession session = session(player, maximum ? Menu.RANKUP_MAX_CONFIRM : Menu.RANKUP_CONFIRM, 0, id);
        Inventory inv = create(player, session, 27);
        fill(inv, session);
        GuiLayout.ConfirmationLayout layout = GuiLayout.CONFIRM_27;
        inv.setItem(layout.cancel(), item(session, theme.cancelMaterial(), "&c&lCancel",
                List.of("&7Return without spending money."), "menu:progression", GuiVisuals.State.DANGEROUS));
        inv.setItem(layout.information(), item(session, Material.EXPERIENCE_BOTTLE, "&b&lRankup Summary",
                List.of("&7Review this purchase carefully.", "", "&7Target Rank: &f"
                        + plan.targetRank().toUpperCase(Locale.ROOT), "&7Cost: &6"
                        + plugin.numbers().currency(plan.cost()), "&7Balance: &f"
                        + plugin.numbers().currency(currentBalance(player)), "", "&8Values are revalidated on click."),
                "none", GuiVisuals.State.STATUS));
        inv.setItem(layout.confirm(), item(session, theme.confirmMaterial(), "&a&lConfirm Rankup",
                List.of("&7Purchase the displayed target rank.", "", "&aClick to confirm."),
                "execute:" + id, GuiVisuals.State.ENABLED));
        player.openInventory(inv);
    }

    private void openPrestige(Player player, int requestedPage) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        List<PrestigeDefinition> values = plugin.prestigeService().definitions();
        int pages = Math.max(1, (values.size() + 35) / 36);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        GuiSession session = session(player, Menu.PRESTIGE, page, "");
        Inventory inv = create(player, session, 54);
        fill(inv, session);
        int current = plugin.prestigeService().indexOf(profile.currentPrestige());
        int start = page * 36;
        for (int index = 0; index < 36 && start + index < values.size(); index++) {
            PrestigeDefinition prestige = values.get(start + index);
            int absolute = start + index;
            boolean completed = absolute < current;
            boolean selected = absolute == current;
            boolean locked = absolute > current;
            String state = completed ? "&aCompleted" : selected ? "&bCurrent Prestige" : "&cLocked";
            List<String> lore = new ArrayList<>();
            lore.add("&7Permanent prestige progression tier.");
            lore.add("");
            lore.addAll(prestige.lore());
            lore.add("&7Cost: &f" + plugin.numbers().currency(prestige.cost()));
            lore.add("&7Sell multiplier: &fx" + prestige.sellMultiplier());
            lore.add("");
            lore.add(state);
            GuiVisuals.State visual = selected ? GuiVisuals.State.CURRENT
                    : locked ? GuiVisuals.State.LOCKED : GuiVisuals.State.STATUS;
            inv.setItem(index, item(session, GuiVisuals.prestigeMaterial(prestige.id(), absolute, locked),
                    (selected ? "&b&l" : completed ? "&d&l" : "&c&l") + prestige.displayName()
                            + (locked ? " &8[LOCKED]" : ""), lore, "none", visual));
        }
        controls(inv, session, page, pages, "menu:main", "page:prestige:");
        inv.setItem(52, item(session, Material.NETHER_STAR, "&d&lPrestige",
                List.of("&7Advance to the next prestige tier.", "", "&7Requires: &fMaximum rank",
                        "&8Command: /prestige"), "confirm:prestige"));
        player.openInventory(inv);
    }

    private void openPrestigeConfirm(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        PrestigeDefinition next = plugin.prestigeService().next(profile.currentPrestige()).orElse(null);
        if (next == null) {
            player.sendMessage(ColorUtil.color("&cYou are already at the maximum prestige."));
            open(player, Menu.PRESTIGE);
            return;
        }
        if (!meetsGuiRequirements(player, profile, next.permission(), next.requirements())) {
            player.sendMessage(ColorUtil.color("&cYou do not meet this prestige's requirements."));
            open(player, Menu.PRESTIGE);
            return;
        }
        String id = UUID.randomUUID().toString();
        long expires = System.currentTimeMillis() + plugin.config().snapshot().guiSecurity().confirmationTimeoutMillis();
        confirmations.put(id, new ConfirmationContext(player.getUniqueId(), Menu.PRESTIGE_CONFIRM,
                profile.currentRank(), profile.currentPrestige(), null, next.id(), next.cost(), expires,
                new AtomicBoolean()));
        GuiSession session = session(player, Menu.PRESTIGE_CONFIRM, 0, id);
        Inventory inv = create(player, session, 27);
        fill(inv, session);
        GuiLayout.ConfirmationLayout layout = GuiLayout.CONFIRM_27;
        inv.setItem(layout.cancel(), item(session, theme.cancelMaterial(), "&c&lCancel",
                List.of("&7Return without prestiging."), "menu:prestige", GuiVisuals.State.DANGEROUS));
        inv.setItem(layout.information(), item(session, Material.NETHER_STAR, "&d&lPrestige Summary",
                List.of("&7Review this permanent progression step.", "", "&7Target: &f" + next.displayName(),
                        "&7Cost: &6" + plugin.numbers().currency(next.cost()), "&7Sell Multiplier: &bx"
                                + next.sellMultiplier(), "", "&8Values are revalidated on click."),
                "none", GuiVisuals.State.STATUS));
        inv.setItem(layout.confirm(), item(session, theme.confirmMaterial(), "&a&lConfirm Prestige",
                List.of("&7Purchase this prestige tier.", "", "&aClick to confirm."),
                "execute:" + id, GuiVisuals.State.ENABLED));
        player.openInventory(inv);
    }

    private void openDangerConfirmation(Player player, String context) {
        DangerConfirmation confirmation = dangerConfirmations.get(context);
        if (confirmation == null || !confirmation.playerId().equals(player.getUniqueId())
                || confirmation.expiresAt() < System.currentTimeMillis()) {
            player.closeInventory();
            player.sendMessage(ColorUtil.color("&cThat confirmation expired."));
            return;
        }
        GuiSession session = session(player, Menu.DANGER_CONFIRM, 0, context);
        Inventory inv = create(player, session, 27);
        fill(inv, session);
        GuiLayout.ConfirmationLayout layout = GuiLayout.CONFIRM_27;
        inv.setItem(layout.cancel(), item(session, theme.cancelMaterial(), "&c&lCancel",
                List.of("&7Return without making changes."), confirmation.backAction(), GuiVisuals.State.DANGEROUS));
        inv.setItem(layout.information(), item(session, theme.deleteMaterial(), "&c&l" + confirmation.title(),
                List.of("&7" + confirmation.description(), "", "&cThis action can affect persistent data.",
                        "&8Permissions and state are revalidated."), "none", GuiVisuals.State.DANGEROUS));
        inv.setItem(layout.confirm(), item(session, theme.confirmMaterial(), "&a&lConfirm",
                List.of("&7Proceed with the displayed action.", "", "&aClick to confirm."),
                "dangerexecute:" + context, GuiVisuals.State.ENABLED));
        player.openInventory(inv);
    }

    private void requestDanger(Player player, String title, String description, String executeAction,
                               String backAction) {
        String id = UUID.randomUUID().toString();
        dangerConfirmations.put(id, new DangerConfirmation(player.getUniqueId(), title, description, executeAction,
                backAction, System.currentTimeMillis() + plugin.config().snapshot().guiSecurity()
                        .confirmationTimeoutMillis(), new AtomicBoolean()));
        open(player, Menu.DANGER_CONFIRM, 0, id);
    }

    private void openSelling(Player player) {
        GuiSession session = session(player, Menu.SELLING, 0, "");
        Inventory inv = create(player, session, 27);
        fill(inv, session);
        BigDecimal base = plugin.sellService().estimatedInventoryBaseValue(player.getUniqueId());
        BigDecimal finalValue = plugin.sellService().estimatedInventoryValue(player.getUniqueId());
        inv.setItem(10, item(session, Material.EMERALD_BLOCK, "&a&lSell Inventory",
                List.of("&7Sell every configured item you carry.", "", "&7Base Value: &f"
                        + plugin.numbers().currency(base), "&7Final Value: &6" + plugin.numbers().currency(finalValue),
                        "", "&8Command: /sellall"), "sellall"));
        inv.setItem(12, item(session, Material.GOLD_INGOT, "&e&lSell Hand",
                List.of("&7Sell only your held item.", "", "&8Command: /sellhand"), "sellhand"));
        inv.setItem(14, item(session, Material.COMPARATOR, "&b&lCurrent Multiplier",
                List.of("&7Your effective sell multiplier.", "", "&7Multiplier: &bx"
                        + plugin.multiplierService().multiplier(player.getUniqueId()).stripTrailingZeros().toPlainString(),
                        "&8Command: /sellvalue"), "none", GuiVisuals.State.CURRENT));
        boolean autoSell = plugin.config().snapshot().features().autoSell();
        inv.setItem(16, item(session, autoSell ? Material.HOPPER : Material.GRAY_DYE,
                (autoSell ? "&a&l" : "&c&l") + "AutoSell",
                List.of("&7Automatically sells committed drops.", "", autoSell
                        ? "&aEnabled server-wide" : "&cDisabled server-wide"), "none",
                autoSell ? GuiVisuals.State.ENABLED : GuiVisuals.State.DISABLED));
        inv.setItem(22, item(session, Material.ARROW, "&cBack", List.of(), "menu:main"));
        player.openInventory(inv);
    }

    private void openBoosters(Player player) {
        GuiSession session = session(player, Menu.BOOSTERS, 0, "");
        Inventory inv = create(player, session, 27);
        fill(inv, session);
        Collection<site.mcrelicworld.relicprison.api.model.BoosterView> boosters =
                plugin.boosterService().activeFor(player.getUniqueId());
        int slot = 9;
        for (var booster : boosters) {
            if (slot >= 18) break;
            long seconds = Math.max(0, (booster.expiresAt() - System.currentTimeMillis()) / 1000L);
            inv.setItem(slot++, item(session, GuiVisuals.boosterMaterial(booster.id()),
                    "&d&lActive Booster &fx" + booster.multiplier(),
                    List.of("&7An active multiplier is improving rewards.", "", "&7Remaining: &b"
                                    + formatDuration(seconds), "&7Scope: &f"
                                    + (booster.serverWide() ? "Server" : "Personal"),
                            "&7ID: &8" + booster.id(), "", "&dActive"), "none", GuiVisuals.State.ACTIVE));
        }
        if (boosters.isEmpty()) inv.setItem(13, item(session, Material.GLASS_BOTTLE,
                "&8&lNo Active Boosters", List.of("&7No personal or server boosters are active.", "",
                        "&8Command: /booster status"), "none", GuiVisuals.State.UNAVAILABLE));
        inv.setItem(22, item(session, Material.ARROW, "&cBack", List.of(), "menu:main"));
        player.openInventory(inv);
    }

    private void openStatistics(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        GuiSession session = session(player, Menu.STATISTICS, 0, "");
        Inventory inv = create(player, session, 45);
        fill(inv, session);
        inv.setItem(10, item(session, Material.DIAMOND_PICKAXE, "&b&lBlocks Mined",
                List.of("&7Your committed mining totals.", "", "&7Lifetime: &f" + plugin.numbers().full(profile.lifetimeBlocks()),
                        "&7Daily: &f" + plugin.numbers().full(profile.dailyBlocks()),
                        "&7Weekly: &f" + plugin.numbers().full(profile.weeklyBlocks()),
                        "&7Monthly: &f" + plugin.numbers().full(profile.monthlyBlocks())),
                "leaderboard:blocks:lifetime"));
        inv.setItem(19, item(session, Material.SUNFLOWER, "&b&lDaily Blocks",
                List.of("&7Today: &f" + plugin.numbers().full(profile.dailyBlocks())), "leaderboard:blocks:daily"));
        inv.setItem(20, item(session, Material.CLOCK, "&b&lWeekly Blocks",
                List.of("&7This week: &f" + plugin.numbers().full(profile.weeklyBlocks())), "leaderboard:blocks:weekly"));
        inv.setItem(21, item(session, Material.MAP, "&b&lMonthly Blocks",
                List.of("&7This month: &f" + plugin.numbers().full(profile.monthlyBlocks())), "leaderboard:blocks:monthly"));
        inv.setItem(12, item(session, Material.GOLD_INGOT, "&6&lMoney Earned",
                List.of("&7Lifetime mining income.", "", "&7Total: &6" + plugin.numbers().currency(profile.moneyEarned())),
                "leaderboard:money:lifetime"));
        inv.setItem(14, item(session, Material.CHEST, "&b&lItems Sold",
                List.of("&7Committed sold-item total.", "", "&7Total: &f"
                        + plugin.numbers().full(plugin.statistics().itemsSold(player.getUniqueId()))),
                "leaderboard:items_sold:lifetime"));
        inv.setItem(16, item(session, Material.RECOVERY_COMPASS, "&b&lPlaytime",
                List.of("&7Time spent on the prison server.", "", "&7Total: &f"
                        + formatDuration(plugin.statistics().playtimeSeconds(player.getUniqueId()))),
                "leaderboard:playtime:lifetime"));
        inv.setItem(28, item(session, Material.EXPERIENCE_BOTTLE, "&b&lRankups",
                List.of("&7Completed rank purchases.", "", "&7Total: &f"
                        + plugin.numbers().full(plugin.statistics().rankups(player.getUniqueId()))),
                "leaderboard:rankups:lifetime"));
        inv.setItem(30, item(session, Material.NETHER_STAR, "&d&lPrestiges",
                List.of("&7Completed prestige advancements.", "", "&7Total: &f"
                        + plugin.numbers().full(plugin.statistics().prestiges(player.getUniqueId()))),
                "leaderboard:prestiges:lifetime"));
        inv.setItem(32, item(session, Material.BLAZE_POWDER, "&d&lBoosters Used",
                List.of("&7Activated booster count.", "", "&7Total: &f"
                        + plugin.numbers().full(plugin.statistics().boostersUsed(player.getUniqueId()))),
                "leaderboard:boosters:lifetime"));
        inv.setItem(34, item(session, Material.NETHER_STAR, "&b&lProgression Boards",
                List.of("&7Highest rank and prestige.", "&8Command: /leaderboard"), "leaderboard:rank:lifetime"));
        inv.setItem(40, item(session, Material.ARROW, "&cBack", List.of(), "menu:main"));
        player.openInventory(inv);
    }

    private void openLeaderboard(Player player, String context) {
        if (!plugin.config().snapshot().features().playerLeaderboards()) {
            player.sendMessage(ColorUtil.color("&cPlayer leaderboards are disabled by the server."));
            return;
        }
        String[] split = context.split(":", 2);
        String metric = split[0];
        String period = split.length > 1 ? split[1] : "lifetime";
        plugin.statistics().leaderboard(metric, period, plugin.config().snapshot().leaderboards().pageSize())
                .whenComplete((entries, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        player.sendMessage(ColorUtil.color("&cUnable to load leaderboard: " + rootMessage(error)));
                        return;
                    }
                    GuiSession session = session(player, Menu.LEADERBOARD, 0, context);
                    Inventory inv = create(player, session, 54);
                    fill(inv, session);
                    int slot = 0;
                    for (LeaderboardEntry entry : entries) {
                        if (slot >= 45) break;
                        boolean self = entry.playerId().equals(player.getUniqueId());
                        Material material = entry.position() == 1 ? Material.GOLD_BLOCK
                                : entry.position() == 2 ? Material.IRON_BLOCK
                                : entry.position() == 3 ? Material.COPPER_BLOCK : Material.PLAYER_HEAD;
                        List<String> lore = List.of("&7Leaderboard result for " + period + ".", "",
                                "&7" + metric + ": &f" + leaderboardValue(metric, entry.value()),
                                "&7Position: &b#" + entry.position(), "", self ? "&bYour Position" : "&8Ranked Player");
                        ItemStack result = material == Material.PLAYER_HEAD
                                ? playerHead(session, entry.playerId(), (self ? "&b&l" : "&f&l") + "#"
                                        + entry.position() + " " + entry.playerName(), lore, "none",
                                        self ? GuiVisuals.State.CURRENT : GuiVisuals.State.STATUS)
                                : item(session, material, (self ? "&b&l" : "&6&l") + "#" + entry.position()
                                        + " " + entry.playerName(), lore, "none",
                                        self ? GuiVisuals.State.CURRENT : GuiVisuals.State.STATUS);
                        inv.setItem(slot++, result);
                    }
                    inv.setItem(49, item(session, Material.ARROW, "&cBack", List.of(), "menu:statistics"));
                    player.openInventory(inv);
                }));
    }

    private void openAdmin(Player player) {
        if (!AdminEditorAccessPolicy.allowed(player::hasPermission)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        GuiSession session = session(player, Menu.ADMIN, 0, "");
        Inventory inv = create(player, session, 45);
        fill(inv, session);
        inv.setItem(4, item(session, Material.COMMAND_BLOCK, "&c&lAdministration",
                List.of("&7Protected RelicPrison management tools.", "", "&cEvery mutation is audited."),
                "none", GuiVisuals.State.STATUS));
        inv.setItem(10, adminSection(session, Material.DIAMOND_PICKAXE, "Mine Manager",
                "Manage mine definitions and teleports.", "admin:admin_mines"));
        inv.setItem(11, adminSection(session, Material.CRAFTING_TABLE, "Composition Editor",
                "Edit weighted mine block compositions.", "admin:admin_composition"));
        inv.setItem(12, adminSection(session, Material.RECOVERY_COMPASS, "Reset Settings",
                "Manage reset timing, retries, and safety.", "admin:admin_reset"));
        inv.setItem(13, adminSection(session, Material.EXPERIENCE_BOTTLE, "Rank Editor",
                "Manage rank order, cost, and display.", "admin:admin_ranks"));
        inv.setItem(14, adminSection(session, Material.NETHER_STAR, "Prestige Editor",
                "Manage prestige tiers and effects.", "admin:admin_prestiges"));
        inv.setItem(15, adminSection(session, Material.GOLD_INGOT, "Sell Prices",
                "Manage vanilla and custom item values.", "admin:admin_sell"));
        inv.setItem(16, adminSection(session, Material.BLAZE_POWDER, "Booster Manager",
                "Manage active and scheduled boosters.", "admin:admin_boosters"));
        inv.setItem(28, adminSection(session, Material.REDSTONE, "Block Events",
                "Manage triggers, limits, and rewards.", "admin:admin_block_events"));
        inv.setItem(29, adminSection(session, Material.COMPARATOR, "Integration Status",
                "Inspect optional plugin integrations.", "admin:admin_integrations"));
        inv.setItem(30, adminSection(session, Material.WRITABLE_BOOK, "Diagnostics",
                "Validate configuration and export diagnostics.", "admin:admin_diagnostics"));
        inv.setItem(31, adminSection(session, Material.PLAYER_HEAD, "Player Progression",
                "Inspect progression and reward recovery.", "admin:admin_players"));
        inv.setItem(40, item(session, Material.ARROW, "&cBack", List.of(), "menu:main"));
        player.openInventory(inv);
    }

    private ItemStack adminSection(GuiSession session, Material material, String title, String description,
                                   String action) {
        return item(session, material, "&e&l" + title,
                List.of("&7" + description, "", "&cStaff tool", "&eClick to open."), action);
    }

    private void openAdminCategory(Player player, Menu menu) {
        if (!AdminEditorAccessPolicy.allowed(player::hasPermission)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        Editor editor = editorFor(menu);
        if (editor != null) {
            openAdminEditorList(player, editor, 0, "");
            return;
        }
        GuiSession session = session(player, menu, 0, "");
        Inventory inv = create(player, session, 27);
        fill(inv, session);
        List<AdminEntry> entries = adminEntries(menu);
        int slot = 10;
        for (AdminEntry entry : entries) {
            inv.setItem(slot++, item(session, entry.material(), entry.title(),
                    List.of("&7" + entry.description(), "", "&8Command: " + entry.command(),
                            "&8Staff: " + player.getUniqueId()), entry.action()));
        }
        inv.setItem(22, item(session, Material.ARROW, "&cBack", List.of(), "menu:admin"));
        player.openInventory(inv);
    }

    private void openAdminEditorList(Player player, Editor editor, int requestedPage, String search) {
        if (!AdminEditorAccessPolicy.allowed(player::hasPermission)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        UUID staffId = player.getUniqueId();
        CompletableFuture.supplyAsync(() -> {
            try { return editor == Editor.BOOSTERS ? boosterSnapshot(requestedPage, search)
                    : adminEditors.snapshot(editor, requestedPage, search); }
            catch (Exception ex) { throw new AdminGuiException(ex); }
        }).whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(staffId);
            if (online == null) return;
            if (error != null) {
                online.sendMessage(ColorUtil.color("&cEditor failed to load: " + rootMessage(error)));
                openAdmin(online);
                return;
            }
            renderAdminEditorList(online, snapshot, search == null ? "" : search);
        }));
    }

    private void renderAdminEditorList(Player player, EditorSnapshot snapshot, String search) {
        Menu menu = menuFor(snapshot.editor());
        GuiSession session = session(player, menu, snapshot.page(), search == null ? "" : search);
        Inventory inv = create(player, session, 54);
        fill(inv, session);
        int slot = 0;
        for (AdminGuiEditorService.EntrySnapshot entry : snapshot.entries()) {
            if (slot >= 36) break;
            String revision = snapshot.revision();
            if (snapshot.editor() == Editor.BOOSTERS && entry.id().startsWith("booster:")) {
                revision = plugin.boosterService().managedBooster(entry.id().substring("booster:".length()))
                        .map(PrisonGuiManager::boosterRevision).orElse("missing");
            }
            String summary = entry.summary();
            GuiVisuals.State state = summary.toLowerCase(Locale.ROOT).contains("disabled")
                    ? GuiVisuals.State.DISABLED : summary.toLowerCase(Locale.ROOT).contains("active")
                    || summary.toLowerCase(Locale.ROOT).contains("enabled") ? GuiVisuals.State.ACTIVE
                    : GuiVisuals.State.CLICKABLE;
            inv.setItem(slot, item(session, entryIcon(snapshot.editor(), entry.id(), slot),
                    (state == GuiVisuals.State.DISABLED ? "&c&l" : state == GuiVisuals.State.ACTIVE
                            ? "&a&l" : "&e&l") + entry.id(),
                    List.of("&7" + summary, "", "&7Revision: &8" + shortRevision(revision), "", "&eClick to edit."),
                    "edit|" + snapshot.editor().name() + "|select|" + entry.id() + "|" + revision, state));
            slot++;
        }
        if (adminEditors.supportsCreate(snapshot.editor())) {
            inv.setItem(46, item(session, Material.LIME_WOOL, "&a&lCreate Entry",
                    List.of("&7Create a validated configuration entry.", "", "&8Example: new_id 1000"),
                    "edit|" + snapshot.editor().name() + "|create||" + snapshot.revision()));
        }
        inv.setItem(47, item(session, theme.searchMaterial(), "&b&lSearch",
                List.of("&7Filter this editor by ID or value.", "", "&eClick, then type search text."),
                "edit|" + snapshot.editor().name() + "|search||" + snapshot.revision()));
        if (snapshot.page() > 0) inv.setItem(45, item(session, theme.backMaterial(), "&ePrevious Page",
                List.of("&7View the previous entries."), "editpage|" + snapshot.editor().name() + "|"
                        + (snapshot.page() - 1) + "|" + safeToken(search)));
        else inv.setItem(45, item(session, theme.unavailablePageMaterial(), "&8Previous Page",
                List.of("&7You are on the first page."), "none", GuiVisuals.State.UNAVAILABLE));
        inv.setItem(48, item(session, Material.MAP, "&bPage " + (snapshot.page() + 1) + " &8/ &f" + snapshot.pages(),
                List.of("&7Results on Page: &f" + snapshot.entries().size(), "&7Filter: &f"
                        + (search == null || search.isBlank() ? "None" : search)), "none", GuiVisuals.State.STATUS));
        inv.setItem(49, item(session, theme.backMaterial(), "&e&lBack", List.of("&7Return to administration."),
                "menu:admin"));
        inv.setItem(50, item(session, theme.closeMaterial(), "&c&lClose", List.of("&7Close this menu."), "close"));
        if (snapshot.page() + 1 < snapshot.pages()) inv.setItem(53, item(session, theme.backMaterial(), "&eNext Page",
                List.of("&7View more entries."), "editpage|" + snapshot.editor().name() + "|"
                        + (snapshot.page() + 1) + "|" + safeToken(search)));
        else inv.setItem(53, item(session, theme.unavailablePageMaterial(), "&8Next Page",
                List.of("&7You are on the last page."), "none", GuiVisuals.State.UNAVAILABLE));
        player.openInventory(inv);
    }

    private void openAdminEditorDetail(Player player, Editor editor, String targetId) {
        if (!AdminEditorAccessPolicy.allowed(player::hasPermission)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        UUID staffId = player.getUniqueId();
        if (editor == Editor.BOOSTERS && targetId.startsWith("booster:")) {
            ActiveBooster booster = plugin.boosterService().managedBooster(targetId.substring("booster:".length())).orElse(null);
            if (booster == null) {
                player.sendMessage(ColorUtil.color("&cThat booster no longer exists."));
                openAdminEditorList(player, editor, 0, "");
            } else renderAdminEditorDetail(player, boosterDetails(booster));
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try { return adminEditors.details(editor, targetId); }
            catch (Exception ex) { throw new AdminGuiException(ex); }
        }).whenComplete((details, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(staffId);
            if (online == null) return;
            if (error != null) {
                online.sendMessage(ColorUtil.color("&cEntry failed to load: " + rootMessage(error)));
                openAdminEditorList(online, editor, 0, "");
                return;
            }
            renderAdminEditorDetail(online, details);
        }));
    }

    private void renderAdminEditorDetail(Player player, EntryDetails details) {
        GuiSession session = session(player, menuFor(details.editor()), 0, details.id());
        Inventory inv = create(player, session, 54);
        fill(inv, session);
        List<String> valueLore = new ArrayList<>();
        valueLore.add("&7" + details.summary());
        for (String value : details.values()) valueLore.add("&8" + abbreviate(value, 48));
        valueLore.add("&7Revision: &f" + shortRevision(details.revision()));
        inv.setItem(4, item(session, entryIcon(details.editor(), details.id(), 0), "&b&l" + details.id(),
                valueLore, "none", GuiVisuals.State.CURRENT));
        int slot = 9;
        for (String property : details.editableProperties()) {
            if (slot >= 36) break;
            inv.setItem(slot++, item(session, GuiVisuals.propertyMaterial(property), "&e&lEdit " + property,
                    List.of("&7Change this validated property.", "", "&eClick, then type the new value."),
                    "edit|" + details.editor().name() + "|set|" + details.id() + "|" + details.revision() + "|" + property));
        }
        boolean runtimeBooster = details.editor() == Editor.BOOSTERS && details.id().startsWith("booster:");
        boolean enabled = details.values().stream().anyMatch(value -> value.toLowerCase(Locale.ROOT)
                .matches(".*enabled[=: ]+true.*"));
        if (adminEditors.supportsToggle(details.editor()) || runtimeBooster) {
            inv.setItem(36, item(session, enabled ? Material.LIME_WOOL : Material.RED_WOOL,
                    (enabled ? "&a&lEnabled" : "&c&lDisabled"),
                    List.of("&7Toggle this entry's runtime state.", "", "&7Current: "
                                    + (enabled ? "&aEnabled" : "&cDisabled"), "&7Revision: &8"
                                    + shortRevision(details.revision())),
                    "edit|" + details.editor().name() + "|toggle|" + details.id() + "|" + details.revision(),
                    enabled ? GuiVisuals.State.ENABLED : GuiVisuals.State.DISABLED));
        }
        if (adminEditors.supportsDelete(details.editor(), details.id()) || runtimeBooster) {
            inv.setItem(37, item(session, theme.deleteMaterial(), "&c&lDelete " + details.id(),
                    List.of("&7Remove this configuration entry.", "", "&7Revision: &8"
                                    + shortRevision(details.revision()), "&cA confirmation is required."),
                    "edit|" + details.editor().name() + "|delete|" + details.id() + "|" + details.revision()));
        }
        if (details.editor() == Editor.MINE_COMPOSITION) {
            inv.setItem(38, item(session, Material.ANVIL, "&aNormalize Composition",
                    List.of("&7Normalizes according to configured weight semantics."),
                    "edit|" + details.editor().name() + "|normalize|" + details.id() + "|" + details.revision()));
            inv.setItem(39, item(session, theme.deleteMaterial(), "&c&lRemove Composition Block",
                    List.of("&7Type the exact block ID to remove.", "&7Revision: &f" + shortRevision(details.revision())),
                    "edit|" + details.editor().name() + "|delete|" + details.id() + "|" + details.revision()));
        }
        inv.setItem(49, item(session, Material.ARROW, "&cBack", List.of(), "admin:" + menuFor(details.editor()).name().toLowerCase(Locale.ROOT)));
        player.openInventory(inv);
    }

    private void handleAdminEditAction(Player player, String action) {
        if (!AdminEditorAccessPolicy.allowed(player::hasPermission)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        String[] parts = action.split("\\|", -1);
        if (parts.length < 4) return;
        Editor editor = Editor.valueOf(parts[1]);
        String verb = parts[2];
        String targetId = parts[3];
        String revision = parts.length > 4 ? parts[4] : "";
        switch (verb) {
            case "select" -> openAdminEditorDetail(player, editor, targetId);
            case "create" -> beginAdminInput(player, new AdminInputSession(editor, Operation.CREATE, "",
                    "", revision, 0, "", System.currentTimeMillis() + inputTimeoutMillis()),
                    editor == Editor.BOOSTERS
                            ? "Type: id multiplier duration <server|personal> <target UUID|-> <now|delay>"
                            : "Type the new " + editor.name().toLowerCase(Locale.ROOT) + " ID and optional initial value.");
            case "search" -> beginAdminInput(player, new AdminInputSession(editor, Operation.CANCEL, "",
                    "__search__", revision, 0, "", System.currentTimeMillis() + inputTimeoutMillis()),
                    "Type search text for " + editor.name().toLowerCase(Locale.ROOT) + ".");
            case "set" -> {
                if (parts.length < 6) return;
                beginAdminInput(player, new AdminInputSession(editor, Operation.SET_PROPERTY, targetId,
                        parts[5], revision, 0, "", System.currentTimeMillis() + inputTimeoutMillis()),
                        editor == Editor.MINE_COMPOSITION
                                ? "Type: block weight [minimum-prestige=id] [allow-air=true] [metadata=value]"
                                : editor == Editor.RESET_SETTINGS
                                ? "Type new value for " + parts[5] + " followed by CONFIRM."
                                : "Type new value for " + parts[5] + ".");
            }
            case "delete" -> {
                if (editor == Editor.MINE_COMPOSITION) {
                    beginAdminInput(player, new AdminInputSession(editor, Operation.DELETE, targetId,
                            "", revision, 0, "", System.currentTimeMillis() + inputTimeoutMillis()),
                            "Type the exact composition block ID to remove.");
                } else {
                    requestDanger(player, "Delete " + targetId, "Delete this "
                                    + editor.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " entry.",
                            "admin|" + editor + "|delete|" + targetId + '|' + revision,
                            "admin:" + menuFor(editor).name().toLowerCase(Locale.ROOT));
                }
            }
            case "toggle" -> {
                AdminInputSession input = new AdminInputSession(editor, Operation.TOGGLE_ENABLED, targetId,
                        "", revision, 0, "", System.currentTimeMillis() + inputTimeoutMillis());
                if (editor == Editor.RESET_SETTINGS) requestDanger(player, "Change Reset Safety",
                        "Apply this reset scheduling or safety change.",
                        "admin|" + editor + "|toggle|" + targetId + '|' + revision,
                        "admin:" + menuFor(editor).name().toLowerCase(Locale.ROOT));
                else submitAdminEdit(player, input, "");
            }
            case "normalize" -> requestDanger(player, "Normalize Composition",
                    "Rewrite this mine composition using configured weight semantics.",
                    "admin|" + editor + "|normalize|" + targetId + '|' + revision,
                    "admin:" + menuFor(editor).name().toLowerCase(Locale.ROOT));
            default -> { }
        }
    }

    public boolean hasPendingAdminInput(UUID playerId) {
        return adminInputs.pending(playerId, System.currentTimeMillis()).isPresent();
    }

    public void handleAdminChatInput(UUID playerId, String playerName, String message) {
        AdminInputSession input = adminInputs.consume(playerId, System.currentTimeMillis()).orElse(null);
        if (input == null) return;
        if ("cancel".equalsIgnoreCase(message.trim())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) openAdminEditorList(player, input.editor(), input.page(), input.search());
            });
            return;
        }
        if ("__search__".equals(input.property())) {
            String searchText = message.trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) openAdminEditorList(player, input.editor(), 0, searchText);
            });
            return;
        }
        if (input.operation() == Operation.DELETE && input.editor() != Editor.MINE_COMPOSITION
                && !"DELETE".equals(message.trim())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(ColorUtil.color("&cDeletion cancelled; confirmation text did not match DELETE."));
                    openAdminEditorDetail(player, input.editor(), input.targetId());
                }
            });
            return;
        }
        if ((input.operation() == Operation.NORMALIZE || input.editor() == Editor.RESET_SETTINGS
                && input.operation() == Operation.TOGGLE_ENABLED) && !"CONFIRM".equals(message.trim())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(ColorUtil.color("&cDangerous change cancelled; confirmation text did not match CONFIRM."));
                    openAdminEditorDetail(player, input.editor(), input.targetId());
                }
            });
            return;
        }
        if (input.editor() == Editor.RESET_SETTINGS && input.operation() == Operation.SET_PROPERTY) {
            String suffix = " CONFIRM";
            if (!message.toUpperCase(Locale.ROOT).endsWith(suffix)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        player.sendMessage(ColorUtil.color("&cReset setting change cancelled; append CONFIRM to the value."));
                        openAdminEditorDetail(player, input.editor(), input.targetId());
                    }
                });
                return;
            }
            message = message.substring(0, message.length() - suffix.length()).trim();
        }
        submitAdminEdit(playerId, playerName, input, message);
    }

    private void beginAdminInput(Player player, AdminInputSession input, String prompt) {
        adminInputs.begin(player.getUniqueId(), input, input.expiresAt());
        player.closeInventory();
        player.sendMessage(ColorUtil.color("&6" + prompt));
        player.sendMessage(ColorUtil.color("&7Type cancel to abort. Input expires in "
                + (inputTimeoutMillis() / 1000L) + " seconds."));
        long delay = Math.max(1L, (input.expiresAt() - System.currentTimeMillis() + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!adminInputs.expire(player.getUniqueId(), input, System.currentTimeMillis())) return;
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online != null) online.sendMessage(ColorUtil.color("&cAdmin edit input expired."));
        }, delay);
    }

    private void submitAdminEdit(Player player, AdminInputSession input, String message) {
        submitAdminEdit(player.getUniqueId(), player.getName(), input, message);
    }

    private void submitAdminEdit(UUID playerId, String playerName, AdminInputSession input, String message) {
        ParsedAdminInput parsed = parseAdminInput(input, message);
        EditRequest request = new EditRequest(input.editor(), input.operation(), parsed.targetId(),
                parsed.property(), parsed.value(), input.expectedRevision(), playerId, playerName);
        if (input.editor() == Editor.BOOSTERS
                && (input.operation() == Operation.CREATE || parsed.targetId().startsWith("booster:"))) {
            submitBoosterEdit(request, input, parsed);
            return;
        }
        CompletableFuture.supplyAsync(() -> adminEditors.apply(request)).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null) return;
                    if (error != null) {
                        player.sendMessage(ColorUtil.color("&cEditor save failed: " + rootMessage(error)));
                    } else {
                        player.sendMessage(ColorUtil.color((result.success() ? "&a" : "&c")
                                + "Editor result: " + result.message()));
                    }
                    if (result != null && "stale-edit-conflict".equals(result.message()) && !parsed.targetId().isBlank()) {
                        player.sendMessage(ColorUtil.color("&eAnother administrator saved first; current values were reloaded."));
                        openAdminEditorDetail(player, input.editor(), parsed.targetId());
                    } else if (result != null && result.success() && !parsed.targetId().isBlank()
                            && !(input.operation() == Operation.SET_PROPERTY && "id".equals(input.property()))) {
                        openAdminEditorDetail(player, input.editor(), parsed.targetId());
                    } else {
                        openAdminEditorList(player, input.editor(), input.page(), input.search());
                    }
                }));
    }

    private EditorSnapshot boosterSnapshot(int requestedPage, String search) throws Exception {
        List<AdminGuiEditorService.EntrySnapshot> entries = new ArrayList<>();
        EditorSnapshot first = adminEditors.snapshot(Editor.BOOSTERS, 0, "");
        entries.addAll(first.entries());
        for (int page = 1; page < first.pages(); page++) entries.addAll(adminEditors.snapshot(Editor.BOOSTERS, page, "").entries());
        long now = System.currentTimeMillis();
        for (ActiveBooster booster : plugin.boosterService().managedBoosters()) {
            String state = !booster.enabled() ? "disabled" : booster.scheduled(now) ? "scheduled" : "active";
            entries.add(new AdminGuiEditorService.EntrySnapshot("booster:" + booster.id(), state + " x"
                    + booster.multiplier() + " " + (booster.serverWide() ? "server" : booster.owner())));
        }
        String needle = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<AdminGuiEditorService.EntrySnapshot> filtered = entries.stream()
                .filter(entry -> needle.isBlank() || entry.id().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.summary().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(AdminGuiEditorService.EntrySnapshot::id)).toList();
        int pages = Math.max(1, (filtered.size() + 35) / 36);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int from = Math.min(filtered.size(), page * 36);
        return new EditorSnapshot(Editor.BOOSTERS, first.revision(), page, pages,
                filtered.subList(from, Math.min(filtered.size(), from + 36)));
    }

    private EntryDetails boosterDetails(ActiveBooster booster) {
        long now = System.currentTimeMillis();
        String state = !booster.enabled() ? "disabled" : booster.scheduled(now) ? "scheduled" : "active";
        List<String> values = List.of("state=" + state, "multiplier=" + booster.multiplier(),
                "scope=" + (booster.serverWide() ? "server" : "personal"),
                "target=" + (booster.owner() == null ? "server" : booster.owner()),
                "starts-at=" + booster.startsAt(), "expires-at=" + booster.expiresAt(),
                "remaining=" + DurationParser.format(Math.max(0L, booster.expiresAt() - now)),
                "enabled=" + booster.enabled());
        return new EntryDetails(Editor.BOOSTERS, boosterRevision(booster), "booster:" + booster.id(), state,
                values, List.of("multiplier", "duration", "scope", "target", "start-at", "enabled"));
    }

    private void submitBoosterEdit(EditRequest request, AdminInputSession input, ParsedAdminInput parsed) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                CompletableFuture<?> operation;
                String before;
                if (request.operation() == Operation.CREATE) {
                    BoosterCreate create = parseBoosterCreate(request.targetId(), request.value());
                    before = "missing:" + create.id();
                    operation = plugin.boosterService().createManaged(create.id(), create.owner(), create.serverWide(),
                            create.multiplier(), create.durationMillis(), create.startsAt(), request.staffId().toString())
                            .thenCompose(result -> result.success() ? CompletableFuture.completedFuture(null)
                                    : CompletableFuture.failedFuture(new IllegalArgumentException(result.message())));
                } else {
                    String id = request.targetId().substring("booster:".length());
                    ActiveBooster booster = plugin.boosterService().managedBooster(id)
                            .orElseThrow(() -> new IllegalArgumentException("Booster no longer exists"));
                    if (!request.expectedRevision().equals(boosterRevision(booster))) {
                        finishBoosterEdit(request, input, parsed, false, "stale-edit-conflict", "", "");
                        return;
                    }
                    before = booster.toString();
                    if (request.operation() == Operation.DELETE) operation = plugin.boosterService().remove(id);
                    else {
                        ActiveBooster updated = editedBooster(booster, request);
                        operation = plugin.boosterService().updateManaged(booster, updated.owner(), updated.serverWide(),
                                updated.multiplier(), updated.startsAt(), updated.expiresAt(), updated.enabled());
                    }
                }
                String beforeValue = before;
                operation.whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) finishBoosterEdit(request, input, parsed, false, rootMessage(error), beforeValue, "");
                    else {
                        String id = request.operation() == Operation.CREATE ? request.targetId()
                                : request.targetId().substring("booster:".length());
                        String after = plugin.boosterService().managedBooster(id).map(Object::toString).orElse("deleted");
                        finishBoosterEdit(request, input, parsed, true, "saved", beforeValue, after);
                    }
                }));
            } catch (RuntimeException error) {
                finishBoosterEdit(request, input, parsed, false, rootMessage(error), "", "");
            }
        });
    }

    private ParsedAdminInput parseAdminInput(AdminInputSession input, String raw) {
        String message = raw == null ? "" : raw.trim();
        if (input.operation() == Operation.CREATE) {
            String[] parts = message.split("\\s+", 2);
            String target = parts.length == 0 ? "" : parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            return new ParsedAdminInput(target, input.property(), value);
        }
        if (input.editor() == Editor.MINE_COMPOSITION && input.operation() == Operation.SET_PROPERTY) {
            String[] parts = message.split("\\s+", 2);
            String property = parts.length == 0 ? "" : parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            return new ParsedAdminInput(input.targetId(), property, value);
        }
        if (input.editor() == Editor.MINE_COMPOSITION && input.operation() == Operation.DELETE) {
            return new ParsedAdminInput(input.targetId(), message, "");
        }
        return new ParsedAdminInput(input.targetId(), input.property(), message);
    }

    private BoosterCreate parseBoosterCreate(String id, String raw) {
        String[] values = raw == null ? new String[0] : raw.trim().split("\\s+");
        if (values.length != 5) throw new IllegalArgumentException(
                "Use: id multiplier duration <server|personal> <target UUID|-> <now|delay>");
        BigDecimal multiplier = finiteDecimal(values[0], "multiplier");
        long duration = DurationParser.parse(values[1]).toMillis();
        boolean serverWide;
        if (values[2].equalsIgnoreCase("server") || values[2].equalsIgnoreCase("global")) serverWide = true;
        else if (values[2].equalsIgnoreCase("personal") || values[2].equalsIgnoreCase("player")) serverWide = false;
        else throw new IllegalArgumentException("Scope must be server or personal");
        UUID owner = serverWide ? null : UUID.fromString(values[3]);
        long startsAt = parseStart(values[4]);
        return new BoosterCreate(id, multiplier, duration, serverWide, owner, startsAt);
    }

    private ActiveBooster editedBooster(ActiveBooster booster, EditRequest request) {
        if (request.operation() == Operation.TOGGLE_ENABLED) {
            return copyBooster(booster, booster.multiplier(), booster.startsAt(), booster.expiresAt(), !booster.enabled());
        }
        if (request.operation() != Operation.SET_PROPERTY) throw new IllegalArgumentException("Unsupported booster operation");
        return switch (request.property().toLowerCase(Locale.ROOT)) {
            case "multiplier" -> copyBooster(booster, finiteDecimal(request.value(), "multiplier"),
                    booster.startsAt(), booster.expiresAt(), booster.enabled());
            case "duration" -> {
                long duration = DurationParser.parse(request.value()).toMillis();
                yield copyBooster(booster, booster.multiplier(), booster.startsAt(),
                        Math.addExact(booster.startsAt(), duration), booster.enabled());
            }
            case "start-at" -> {
                long start = parseStart(request.value());
                long duration = Math.max(1L, booster.expiresAt() - booster.startsAt());
                yield copyBooster(booster, booster.multiplier(), start, Math.addExact(start, duration), booster.enabled());
            }
            case "scope" -> editBoosterScope(booster, request.value());
            case "target" -> editBoosterTarget(booster, request.value());
            case "enabled" -> copyBooster(booster, booster.multiplier(), booster.startsAt(), booster.expiresAt(),
                    parseBooleanValue(request.value()));
            default -> throw new IllegalArgumentException("Unsupported booster property: " + request.property());
        };
    }

    private static ActiveBooster copyBooster(ActiveBooster booster, BigDecimal multiplier, long startsAt,
                                             long expiresAt, boolean enabled) {
        return copyBooster(booster, booster.owner(), booster.serverWide(), multiplier, startsAt, expiresAt, enabled);
    }

    private static ActiveBooster copyBooster(ActiveBooster booster, UUID owner, boolean serverWide,
                                             BigDecimal multiplier, long startsAt, long expiresAt, boolean enabled) {
        if (expiresAt <= startsAt) throw new IllegalArgumentException("Booster duration must be positive");
        return new ActiveBooster(booster.id(), owner, multiplier, expiresAt, booster.createdAt(),
                serverWide, booster.activatedBy(), startsAt, enabled);
    }

    private static ActiveBooster editBoosterScope(ActiveBooster booster, String raw) {
        String value = raw.trim();
        if (value.equalsIgnoreCase("server") || value.equalsIgnoreCase("global")) {
            return copyBooster(booster, null, true, booster.multiplier(), booster.startsAt(),
                    booster.expiresAt(), booster.enabled());
        }
        String ownerText = value.replaceFirst("(?i)^personal[:\\s]+", "");
        UUID owner = ownerText.equalsIgnoreCase("personal") || ownerText.isBlank()
                ? booster.owner() : UUID.fromString(ownerText);
        if (owner == null) throw new IllegalArgumentException("Use personal:<player UUID> when changing server scope");
        return copyBooster(booster, owner, false, booster.multiplier(), booster.startsAt(),
                booster.expiresAt(), booster.enabled());
    }

    private static ActiveBooster editBoosterTarget(ActiveBooster booster, String raw) {
        String value = raw.trim();
        if (value.equalsIgnoreCase("server") || value.equals("-")) {
            return copyBooster(booster, null, true, booster.multiplier(), booster.startsAt(),
                    booster.expiresAt(), booster.enabled());
        }
        UUID owner = UUID.fromString(value);
        return copyBooster(booster, owner, false, booster.multiplier(), booster.startsAt(),
                booster.expiresAt(), booster.enabled());
    }

    private void finishBoosterEdit(EditRequest request, AdminInputSession input, ParsedAdminInput parsed,
                                   boolean success, String reason, String before, String after) {
        adminEditors.recordExternal(request, before, after, success, reason);
        Player player = Bukkit.getPlayer(request.staffId());
        if (player == null) return;
        player.sendMessage(ColorUtil.color((success ? "&a" : "&c") + "Editor result: " + reason));
        if (!success && reason.equals("stale-edit-conflict") && !request.targetId().isBlank()) {
            player.sendMessage(ColorUtil.color("&eAnother administrator saved first; current values were reloaded."));
            openAdminEditorDetail(player, Editor.BOOSTERS, request.targetId());
        } else if (success && request.operation() != Operation.DELETE && request.operation() != Operation.CREATE) {
            openAdminEditorDetail(player, Editor.BOOSTERS, request.targetId());
        } else openAdminEditorList(player, Editor.BOOSTERS, input.page(), input.search());
    }

    private static long parseStart(String raw) {
        if (raw.equalsIgnoreCase("now")) return System.currentTimeMillis();
        if (raw.matches("[0-9]{11,}")) return Long.parseLong(raw);
        String delay = raw.toLowerCase(Locale.ROOT).startsWith("in:") ? raw.substring(3) : raw;
        return Math.addExact(System.currentTimeMillis(), DurationParser.parse(delay).toMillis());
    }

    private static BigDecimal finiteDecimal(String raw, String name) {
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() <= 0 || !Double.isFinite(value.doubleValue()))
                throw new IllegalArgumentException(name + " must be positive and finite");
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " must be numeric", error);
        }
    }

    private static boolean parseBooleanValue(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("yes") || raw.equalsIgnoreCase("on")) return true;
        if (raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("no") || raw.equalsIgnoreCase("off")) return false;
        throw new IllegalArgumentException("Value must be true or false");
    }

    private static String boosterRevision(ActiveBooster booster) {
        return UUID.nameUUIDFromBytes(booster.toString().getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private Editor editorFor(Menu menu) {
        return switch (menu) {
            case ADMIN_RANKS -> Editor.RANKS;
            case ADMIN_PRESTIGES -> Editor.PRESTIGES;
            case ADMIN_SELL -> Editor.SELL_PRICES;
            case ADMIN_BOOSTERS -> Editor.BOOSTERS;
            case ADMIN_BLOCK_EVENTS -> Editor.BLOCK_EVENTS;
            case ADMIN_RESET -> Editor.RESET_SETTINGS;
            case ADMIN_COMPOSITION -> Editor.MINE_COMPOSITION;
            default -> null;
        };
    }

    private Menu menuFor(Editor editor) {
        return switch (editor) {
            case RANKS -> Menu.ADMIN_RANKS;
            case PRESTIGES -> Menu.ADMIN_PRESTIGES;
            case SELL_PRICES -> Menu.ADMIN_SELL;
            case BOOSTERS -> Menu.ADMIN_BOOSTERS;
            case BLOCK_EVENTS -> Menu.ADMIN_BLOCK_EVENTS;
            case RESET_SETTINGS -> Menu.ADMIN_RESET;
            case MINE_COMPOSITION -> Menu.ADMIN_COMPOSITION;
        };
    }

    private Material entryIcon(Editor editor, String id, int index) {
        return switch (editor) {
            case SELL_PRICES -> {
                String key = id.toUpperCase(Locale.ROOT);
                if (key.startsWith("MINECRAFT:")) key = key.substring("MINECRAFT:".length());
                Material material = Material.matchMaterial(key);
                yield material != null && material.isItem() ? material : Material.CHEST;
            }
            case RANKS -> GuiVisuals.rankMaterial(Math.max(0, index), 36, false);
            case PRESTIGES -> GuiVisuals.prestigeMaterial(id, index, false);
            case BOOSTERS -> GuiVisuals.boosterMaterial(id);
            case BLOCK_EVENTS -> GuiVisuals.eventMaterial(id);
            case RESET_SETTINGS -> GuiVisuals.propertyMaterial(id);
            case MINE_COMPOSITION -> GuiVisuals.mineMaterial(id, id, index, true);
        };
    }

    private List<AdminEntry> adminEntries(Menu menu) {
        return switch (menu) {
            case ADMIN_MINES -> List.of(
                    admin(Material.MAP, "&aList Mines", "Show configured mines.", "/relicmine list", "run:relicmine list"),
                    admin(Material.ENDER_PEARL, "&aOpen Mine Teleport GUI", "Use existing mine GUI.", "/relicmine gui", "run:relicmine gui"),
                    admin(Material.TNT, "&cDelete Mine", "Destructive action requires command confirmation.", "/relicmine delete <mine>", "none"));
            case ADMIN_COMPOSITION -> List.of(admin(Material.CRAFTING_TABLE, "&aComposition Help",
                    "Open mine composition command workflow.", "/relicmine composition <mine>", "run:relicmine composition"));
            case ADMIN_RESET -> List.of(
                    admin(Material.CLOCK, "&aReset Mine", "Run configured reset validation.", "/relicmine reset <mine>", "none"),
                    admin(Material.REDSTONE_TORCH, "&aReset Setting Commands", "Open reset setting command workflow.", "/relicmine resetconfig <mine>", "none"));
            case ADMIN_RANKS -> List.of(admin(Material.EXPERIENCE_BOTTLE, "&aRank Commands",
                    "Set, promote, or demote loaded profiles.", "/relicrank info|set|promote|demote", "none"));
            case ADMIN_PRESTIGES -> List.of(admin(Material.NETHER_STAR, "&aPrestige Commands",
                    "Set, promote, or demote prestige.", "/relicprestige info|set|promote|demote", "none"));
            case ADMIN_SELL -> List.of(admin(Material.GOLD_INGOT, "&aReload Sell Prices",
                    "Atomically validate and reload sell prices.", "/rp reload selling", "run:rp reload selling"));
            case ADMIN_BOOSTERS -> List.of(
                    admin(Material.BEACON, "&aList Boosters", "Show server boosters.", "/booster list", "run:booster list"),
                    admin(Material.EMERALD, "&aGive Booster", "Use command arguments for target and duration.", "/booster give <player> <type> <multiplier> <duration>", "none"));
            case ADMIN_BLOCK_EVENTS -> List.of(admin(Material.REDSTONE, "&aReload Block Events",
                    "Atomically validate Block Events.", "/rp reload block-events", "run:rp reload block-events"));
            case ADMIN_INTEGRATIONS -> List.of(admin(Material.COMPARATOR, "&aStatus",
                    "Show integration status.", "/rp status", "run:rp status"));
            case ADMIN_DIAGNOSTICS -> List.of(
                    admin(Material.WRITABLE_BOOK, "&aValidate", "Run config validation.", "/rp validate", "run:rp validate"),
                    admin(Material.BOOK, "&aDiagnostics", "Create diagnostic export.", "/rp diagnostic", "run:rp diagnostic"));
            case ADMIN_PLAYERS -> List.of(
                    admin(Material.PLAYER_HEAD, "&aProgression Transactions", "Inspect crash-safe rankup/prestige transactions.", "/rp progression list", "run:rp progression list"),
                    admin(Material.NETHER_STAR, "&aLeaderboard Rewards", "Inspect pending/failed rewards.", "/rp rewards pending", "run:rp rewards pending"));
            default -> List.of();
        };
    }

    public void handle(Player player, ItemStack clicked, Holder holder) {
        if (!validSession(player, clicked, holder)) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.equals("none")) return;
        if (coolingDown(player)) return;
        if (action.startsWith("deny:")) {
            play(player, theme.deniedSound());
            player.sendMessage(ColorUtil.color("&c" + action.substring("deny:".length())));
            return;
        }
        play(player, theme.clickSound());
        if (action.equals("close")) {
            player.closeInventory();
            return;
        }
        if (action.startsWith("menu:")) {
            openMenuAction(player, action.substring(5));
            return;
        }
        if (action.startsWith("page:")) {
            String[] p = action.split(":");
            if (p.length == 3) open(player, Menu.valueOf(p[1].toUpperCase(Locale.ROOT)), Integer.parseInt(p[2]), "");
            return;
        }
        if (action.startsWith("editpage|")) {
            String[] parts = action.split("\\|", -1);
            if (parts.length >= 4) {
                openAdminEditorList(player, Editor.valueOf(parts[1]), Integer.parseInt(parts[2]), unsafedToken(parts[3]));
            }
            return;
        }
        if (action.startsWith("edit|")) {
            handleAdminEditAction(player, action);
            return;
        }
        if (action.equals("gangrank:create")) {
            player.closeInventory();
            gangRankInputs.put(player.getUniqueId(), new GangRankInput(GangRankInputType.CREATE, null,
                    "", 0, "", System.currentTimeMillis() + 60_000L));
            player.sendMessage(ColorUtil.color("&eEnter: <name> <priority> <color>, or type cancel."));
            return;
        }
        if (action.startsWith("gangrank:field:")) {
            String[] parts = action.split(":", 4);
            if (parts.length == 4) beginGangRankFieldInput(player, parts[2], parts[3]);
            return;
        }
        if (action.startsWith("gangrank:permissions:")) {
            open(player, Menu.GANG_RANK_PERMISSIONS, 0, action.substring("gangrank:permissions:".length()));
            return;
        }
        if (action.startsWith("gangrank:permission:")) {
            String[] parts = action.split(":", 5);
            if (parts.length == 5) {
                UUID rankId = UUID.fromString(parts[2]);
                var permission = site.mcrelicworld.relicprison.gang.GangPermission.parse(parts[3]);
                plugin.gangs().setRankPermission(player.getUniqueId(), rankId, permission,
                        Boolean.parseBoolean(parts[4])).whenComplete((result, error) -> sync(() -> {
                    player.sendMessage(error == null ? result.message() : rootMessage(error));
                    open(player, Menu.GANG_RANK_PERMISSIONS, 0, rankId.toString());
                }));
            }
            return;
        }
        if (action.startsWith("gangrank:delete:")) {
            UUID rankId = UUID.fromString(action.substring("gangrank:delete:".length()));
            requestDanger(player, "Delete Custom Rank", "Members assigned to this rank will move to Recruit.",
                    "gangrankdelete|" + rankId, "gangrank:edit:" + rankId);
            return;
        }
        if (action.startsWith("gangrank:edit:")) {
            open(player, Menu.GANG_RANK_EDIT, 0, action.substring("gangrank:edit:".length()));
            return;
        }
        if (action.startsWith("admin:")) {
            if (!AdminEditorAccessPolicy.allowed(player::hasPermission)) {
                plugin.messages().send(player, "no-permission");
                return;
            }
            openMenuAction(player, action.substring(6));
            return;
        }
        if (action.startsWith("run:")) {
            if (!AdminEditorAccessPolicy.allowed(player::hasPermission)) {
                plugin.messages().send(player, "no-permission");
                return;
            }
            plugin.getLogger().info("Admin GUI command by " + player.getUniqueId() + ": /" + action.substring(4));
            player.closeInventory();
            player.performCommand(action.substring(4));
            return;
        }
        if (action.startsWith("mine:")) {
            play(player, theme.teleportSound());
            player.closeInventory();
            plugin.mineTeleports().teleport(player, action.substring(5));
            return;
        }
        if (action.startsWith("leaderboard:")) {
            open(player, Menu.LEADERBOARD, 0, action.substring("leaderboard:".length()));
            return;
        }
        if (action.startsWith("gangmember:")) {
            open(player, Menu.GANG_MEMBER, 0, action.substring("gangmember:".length()));
            return;
        }
        if (action.startsWith("gangcmd:transfer:")) {
            String target = action.substring("gangcmd:transfer:".length());
            requestDanger(player, "Transfer Gang Ownership", "Make " + target + " the permanent gang owner.",
                    "gangcmd|transfer " + target, "menu:gang_members");
            return;
        }
        if (action.startsWith("gangcmd:kick:")) {
            String target = action.substring("gangcmd:kick:".length());
            requestDanger(player, "Kick Gang Member", "Remove " + target + " from this gang.",
                    "gangcmd|kick " + target, "menu:gang_members");
            return;
        }
        if (action.equals("gangdanger:disband")) {
            requestDanger(player, "Disband Gang", "Disable the gang and remove its active membership.",
                    "gangdisband", "menu:gang_settings");
            return;
        }
        if (action.startsWith("gangcmd:")) {
            player.closeInventory();
            player.performCommand("gang " + action.substring("gangcmd:".length()).replace(':', ' '));
            return;
        }
        if (action.startsWith("confirm:")) {
            if (action.endsWith("rankup")) openRankupConfirm(player, false);
            else if (action.endsWith("rankupmax")) openRankupConfirm(player, true);
            else if (action.endsWith("prestige")) openPrestigeConfirm(player);
            return;
        }
        if (action.startsWith("execute:")) {
            executeConfirmation(player, action.substring("execute:".length()));
            return;
        }
        if (action.startsWith("dangerexecute:")) {
            executeDanger(player, action.substring("dangerexecute:".length()));
            return;
        }
        player.closeInventory();
        switch (action) {
            case "sellall" -> player.performCommand("sellall");
            case "sellhand" -> player.performCommand("sellhand");
            default -> { }
        }
    }

    private void openMenuAction(Player player, String rawMenu) {
        try {
            open(player, Menu.valueOf(rawMenu.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            player.closeInventory();
        }
    }

    private void beginGangRankFieldInput(Player player, String field, String rawRankId) {
        UUID rankId = UUID.fromString(rawRankId);
        plugin.gangs().repository().rank(rankId).whenComplete((loaded, error) -> sync(() -> {
            if (error != null || loaded.isEmpty()) { player.sendMessage(ColorUtil.color("&cRank not found")); return; }
            GangRank rank = loaded.get();
            GangRankInputType type = GangRankInputType.valueOf(field.toUpperCase(Locale.ROOT));
            gangRankInputs.put(player.getUniqueId(), new GangRankInput(type, rankId, rank.displayName(),
                    rank.priority(), rank.color(), System.currentTimeMillis() + 60_000L));
            player.closeInventory();
            player.sendMessage(ColorUtil.color("&eEnter the new " + field + " in chat, or type cancel."));
        }));
    }

    public boolean hasPendingGangRankInput(UUID playerId) {
        GangRankInput input = gangRankInputs.get(playerId);
        if (input == null) return false;
        if (input.expiresAt() >= System.currentTimeMillis()) return true;
        gangRankInputs.remove(playerId);
        return false;
    }

    public void handleGangRankChatInput(UUID playerId, String message) {
        GangRankInput input = gangRankInputs.remove(playerId);
        if (input == null || input.expiresAt() < System.currentTimeMillis()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) return;
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ColorUtil.color("&eGang rank edit cancelled."));
                open(player, Menu.GANG_RANKS);
                return;
            }
            try {
                if (input.type() == GangRankInputType.CREATE) {
                    String[] values = message.trim().split("\\s+", 3);
                    if (values.length != 3) throw new IllegalArgumentException("Expected: <name> <priority> <color>");
                    plugin.gangs().createRank(playerId, values[0], Integer.parseInt(values[1]), values[2], Set.of())
                            .whenComplete((rank, error) -> sync(() -> {
                                player.sendMessage(error == null ? "Rank created: " + rank.displayName() : rootMessage(error));
                                open(player, Menu.GANG_RANKS);
                            }));
                    return;
                }
                String name = input.type() == GangRankInputType.NAME ? message.trim() : input.name();
                int priority = input.type() == GangRankInputType.PRIORITY
                        ? Integer.parseInt(message.trim()) : input.priority();
                String color = input.type() == GangRankInputType.COLOR ? message.trim() : input.color();
                plugin.gangs().editRank(playerId, input.rankId(), name, priority, color)
                        .whenComplete((result, error) -> sync(() -> {
                            player.sendMessage(error == null ? result.message() : rootMessage(error));
                            open(player, Menu.GANG_RANK_EDIT, 0, input.rankId().toString());
                        }));
            } catch (RuntimeException error) {
                player.sendMessage(ColorUtil.color("&c" + rootMessage(error)));
                open(player, Menu.GANG_RANKS);
            }
        });
    }

    private boolean validSession(Player player, ItemStack clicked, Holder holder) {
        if (clicked == null || !clicked.hasItemMeta()) return false;
        GuiSession current = sessions.get(player.getUniqueId());
        if (current == null || !current.id().equals(holder.sessionId()) || current.generation() != holder.generation()
                || holder.generation() != generation.get()) {
            player.closeInventory();
            return false;
        }
        if (System.currentTimeMillis() - current.openedAt() > plugin.config().snapshot().guiSecurity()
                .sessionTimeoutMillis()) {
            player.closeInventory();
            clear(player.getUniqueId());
            return false;
        }
        String itemSession = clicked.getItemMeta().getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
        if (!holder.sessionId().toString().equals(itemSession)) return false;
        return true;
    }

    private boolean coolingDown(Player player) {
        long now = System.currentTimeMillis();
        long previous = lastClicks.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < plugin.config().snapshot().guiSecurity().clickCooldownMillis()) return true;
        lastClicks.put(player.getUniqueId(), now);
        return false;
    }

    private void executeDanger(Player player, String id) {
        DangerConfirmation confirmation = dangerConfirmations.remove(id);
        if (confirmation == null || !confirmation.playerId().equals(player.getUniqueId())
                || confirmation.expiresAt() < System.currentTimeMillis()
                || !confirmation.submitted().compareAndSet(false, true)) {
            player.closeInventory();
            player.sendMessage(ColorUtil.color("&cThat confirmation expired or was already used."));
            play(player, theme.deniedSound());
            return;
        }
        play(player, theme.deleteSound());
        String action = confirmation.executeAction();
        if (action.startsWith("gangcmd|")) {
            player.closeInventory();
            player.performCommand("gang " + action.substring("gangcmd|".length()));
            return;
        }
        if (action.equals("gangdisband")) {
            player.closeInventory();
            plugin.gangs().disband(player.getUniqueId()).whenComplete((result, error) -> sync(() -> {
                player.sendMessage(error == null ? result.message() : rootMessage(error));
                if (error == null && result.success()) play(player, theme.successSound());
            }));
            return;
        }
        if (action.startsWith("gangrankdelete|")) {
            UUID rankId = UUID.fromString(action.substring("gangrankdelete|".length()));
            plugin.gangs().ranks(player.getUniqueId()).thenCompose(values -> {
                GangRank fallback = values.stream().filter(rank -> rank.systemKey().equals("recruit"))
                        .findFirst().orElseThrow();
                return plugin.gangs().deleteRank(player.getUniqueId(), rankId, fallback.id());
            }).whenComplete((result, error) -> sync(() -> {
                player.sendMessage(error == null ? result.message() : rootMessage(error));
                if (error == null && result.success()) play(player, theme.successSound());
                open(player, Menu.GANG_RANKS);
            }));
            return;
        }
        if (action.startsWith("admin|")) {
            String[] parts = action.split("\\|", -1);
            if (parts.length != 5) return;
            Editor editor = Editor.valueOf(parts[1]);
            Operation operation = switch (parts[2]) {
                case "delete" -> Operation.DELETE;
                case "toggle" -> Operation.TOGGLE_ENABLED;
                case "normalize" -> Operation.NORMALIZE;
                default -> throw new IllegalArgumentException("Unknown confirmed editor action");
            };
            AdminInputSession input = new AdminInputSession(editor, operation, parts[3], "", parts[4],
                    0, "", System.currentTimeMillis() + inputTimeoutMillis());
            submitAdminEdit(player, input, operation == Operation.DELETE ? "DELETE" : "CONFIRM");
        }
    }

    private void executeConfirmation(Player player, String id) {
        ConfirmationContext context = confirmations.get(id);
        if (context == null || !context.playerId().equals(player.getUniqueId())
                || System.currentTimeMillis() > context.expiresAt()) {
            player.closeInventory();
            player.sendMessage(ColorUtil.color("&cThat confirmation expired. Please reopen the menu."));
            return;
        }
        if (!context.submitted().compareAndSet(false, true)) return;
        PlayerProfile profile = profile(player);
        if (profile == null || !safeEquals(profile.currentRank(), context.previousRank())
                || !safeEquals(profile.currentPrestige(), context.previousPrestige())) {
            player.closeInventory();
            player.sendMessage(ColorUtil.color("&cYour progression changed. Please reopen the menu."));
            return;
        }
        if (context.menu() == Menu.RANKUP_CONFIRM || context.menu() == Menu.RANKUP_MAX_CONFIRM) {
            boolean maximum = context.menu() == Menu.RANKUP_MAX_CONFIRM;
            RankPlan plan = rankPlan(player, profile, currentBalance(player), maximum);
            if (plan == null || !plan.targetRank().equals(context.targetRank())
                    || plan.cost().compareTo(context.cost()) != 0 || !player.hasPermission(maximum
                    ? "relicprison.rankupmax" : "relicprison.rankup")) {
                player.closeInventory();
                player.sendMessage(ColorUtil.color("&cThat rankup price or permission is stale."));
                return;
            }
            player.closeInventory();
            plugin.progression().rankUp(player.getUniqueId(), maximum).whenComplete((result, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null) plugin.messages().send(player, "progression-error",
                                Map.of("error", rootMessage(error)));
                        else if (!result.success()) sendProgressionFailure(player, result.code(), maximum);
                    }));
            return;
        }
        PrestigeDefinition next = plugin.prestigeService().next(profile.currentPrestige()).orElse(null);
        if (next == null || !next.id().equals(context.targetPrestige()) || next.cost().compareTo(context.cost()) != 0
                || !player.hasPermission("relicprison.prestige")
                || !meetsGuiRequirements(player, profile, next.permission(), next.requirements())) {
            player.closeInventory();
            player.sendMessage(ColorUtil.color("&cThat prestige price or permission is stale."));
            return;
        }
        player.closeInventory();
        plugin.progression().prestige(player.getUniqueId()).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) plugin.messages().send(player, "progression-error",
                            Map.of("error", rootMessage(error)));
                    else if (!result.success()) sendPrestigeFailure(player, result.code());
                }));
    }

    private RankPlan rankPlan(Player player, PlayerProfile profile, BigDecimal balance, boolean maximum) {
        int current = plugin.rankService().indexOf(profile.currentRank());
        List<RankDefinition> ranks = plugin.rankService().definitions();
        if (current < 0) return null;
        BigDecimal multiplier = plugin.prestigeService().rankCostMultiplier(profile.currentPrestige());
        BigDecimal total = BigDecimal.ZERO;
        RankDefinition cursor = ranks.get(current);
        RankDefinition target = cursor;
        while (true) {
            RankDefinition candidate = plugin.rankService().next(cursor.id()).orElse(null);
            if (candidate == null || !meetsGuiRequirements(player, profile, candidate.permission(), candidate.requirements())) break;
            BigDecimal cost = plugin.rankService().cumulativeCost(cursor.id(), candidate.id(), multiplier);
            if (total.add(cost).compareTo(balance) > 0) break;
            total = total.add(cost);
            target = candidate;
            cursor = candidate;
            if (!maximum) break;
        }
        return target == ranks.get(current) ? null : new RankPlan(target.id(), total);
    }

    private boolean meetsGuiRequirements(Player player, PlayerProfile profile, String permission, List<String> requirements) {
        if (permission != null && !player.hasPermission(permission)) return false;
        for (String requirement : requirements) {
            String[] parts = requirement.toLowerCase(Locale.ROOT).split(":", 2);
            if (parts.length != 2) return false;
            if (parts[0].equals("permission") && !player.hasPermission(parts[1])) return false;
            if (parts[0].equals("rank") && plugin.rankService().indexOf(profile.currentRank())
                    < plugin.rankService().indexOf(parts[1])) return false;
            if (parts[0].equals("prestige") && plugin.prestigeService().indexOf(profile.currentPrestige())
                    < plugin.prestigeService().indexOf(parts[1])) return false;
        }
        return true;
    }

    private PlayerProfile profile(Player player) {
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(player.getUniqueId()).orElse(null);
        if (profile == null) player.sendMessage(ColorUtil.color("&cYour profile is still loading."));
        return profile;
    }

    private void sendProgressionFailure(Player player, String code, boolean maximum) {
        String key = switch (code) {
            case "profile-loading" -> "database-unavailable";
            case "already-processing" -> "progression-processing";
            case "max-rank" -> "rankup-max-rank";
            case "not-enough-money" -> "rankup-insufficient";
            case "cancelled" -> "progression-cancelled";
            default -> maximum ? "progression-failed" : "progression-failed";
        };
        plugin.messages().send(player, key);
    }

    private void sendPrestigeFailure(Player player, String code) {
        String key = switch (code) {
            case "profile-loading" -> "database-unavailable";
            case "already-processing" -> "progression-processing";
            case "requires-max-rank" -> "prestige-requires-z";
            case "max-prestige" -> "prestige-max";
            case "not-enough-money" -> "prestige-insufficient";
            case "cancelled" -> "progression-cancelled";
            default -> "progression-failed";
        };
        plugin.messages().send(player, key);
    }

    private BigDecimal currentBalance(Player player) { return plugin.economy().balance(player); }

    private ItemStack item(GuiSession session, Material material, String name, List<String> lore, String action) {
        return item(session, material, name, lore, action, GuiVisuals.inferState(name, lore, action));
    }

    private ItemStack item(GuiSession session, Material material, String name, List<String> lore, String action,
                           GuiVisuals.State state) {
        String plainName = ColorUtil.plain(name);
        Material effectiveMaterial = navigationMaterial(material, plainName);
        String effectiveName = navigationName(name, plainName);
        return GuiItemBuilder.of(effectiveMaterial)
                .name(GuiVisuals.styledName(effectiveName, state))
                .lore(GuiVisuals.structuredLore(lore, state, action))
                .glow(state.glow())
                .hideFlags()
                .stringData(actionKey, action)
                .stringData(sessionKey, session.id().toString())
                .build();
    }

    private Material navigationMaterial(Material fallback, String plainName) {
        if (plainName == null) return fallback;
        String normalized = plainName.toLowerCase(Locale.ROOT);
        if (normalized.equals("back") || normalized.contains("previous page") || normalized.contains("next page")) {
            return theme.backMaterial();
        }
        if (normalized.equals("close")) return theme.closeMaterial();
        if (normalized.startsWith("confirm")) return theme.confirmMaterial();
        if (normalized.equals("cancel")) return theme.cancelMaterial();
        if (normalized.startsWith("delete") || normalized.startsWith("remove composition")) {
            return theme.deleteMaterial();
        }
        if (normalized.startsWith("search")) return theme.searchMaterial();
        return fallback;
    }

    private static String navigationName(String fallback, String plainName) {
        if (plainName == null) return fallback;
        String normalized = plainName.toLowerCase(Locale.ROOT);
        if (normalized.equals("back")) return "&e&lBack";
        if (normalized.contains("previous page")) return "&ePrevious Page";
        if (normalized.contains("next page")) return "&eNext Page";
        if (normalized.equals("close")) return "&c&lClose";
        if (normalized.startsWith("confirm")) return "&a&l" + plainName;
        if (normalized.equals("cancel")) return "&c&lCancel";
        if (normalized.startsWith("delete") || normalized.startsWith("remove composition")) {
            return "&c&l" + plainName;
        }
        if (normalized.startsWith("search")) return "&b&l" + plainName;
        return fallback;
    }

    private ItemStack playerHead(GuiSession session, UUID playerId, String name, List<String> lore, String action,
                                 GuiVisuals.State state) {
        return GuiItemBuilder.of(Material.PLAYER_HEAD)
                .name(GuiVisuals.styledName(name, state))
                .lore(GuiVisuals.structuredLore(lore, state, action))
                .playerHead(playerId)
                .glow(state.glow())
                .hideFlags()
                .stringData(actionKey, action)
                .stringData(sessionKey, session.id().toString())
                .build();
    }

    private void fill(Inventory inventory, GuiSession session) {
        ItemStack filler = GuiItemBuilder.of(fillerMaterial(session.menu())).name("&8 ")
                .stringData(actionKey, "none").stringData(sessionKey, session.id().toString()).build();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        inventory.setItem(inventory.getSize() - 1, item(session, theme.closeMaterial(), "&c&lClose",
                List.of("&7Close this menu."), "close", GuiVisuals.State.DANGEROUS));
    }

    private void controls(Inventory inv, GuiSession session, int page, int pages, String back, String prefix) {
        if (page > 0) inv.setItem(45, item(session, theme.backMaterial(), "&ePrevious Page",
                List.of("&7View the previous entries."), prefix + (page - 1)));
        else inv.setItem(45, item(session, theme.unavailablePageMaterial(), "&8Previous Page",
                List.of("&7You are on the first page."), "none", GuiVisuals.State.UNAVAILABLE));
        inv.setItem(48, item(session, Material.MAP, "&bPage " + (page + 1) + " &8/ &f" + pages,
                List.of("&7Use the arrows to browse."), "none", GuiVisuals.State.STATUS));
        inv.setItem(49, item(session, theme.backMaterial(), "&e&lBack",
                List.of("&7Return to the previous menu."), back, GuiVisuals.State.CLICKABLE));
        inv.setItem(50, item(session, theme.closeMaterial(), "&c&lClose",
                List.of("&7Close this menu."), "close", GuiVisuals.State.DANGEROUS));
        if (page + 1 < pages) inv.setItem(53, item(session, theme.backMaterial(), "&eNext Page",
                List.of("&7View more entries."), prefix + (page + 1)));
        else inv.setItem(53, item(session, theme.unavailablePageMaterial(), "&8Next Page",
                List.of("&7You are on the last page."), "none", GuiVisuals.State.UNAVAILABLE));
    }

    private static int pages(int size, int pageSize) {
        return GuiLayout.pages(size, pageSize);
    }

    private static int boundedPage(int requestedPage, int pages) {
        return GuiLayout.boundedPage(requestedPage, pages);
    }

    private static <T> List<T> page(List<T> values, int page, int pageSize) {
        int start = Math.min(values.size(), Math.max(0, page) * pageSize);
        return values.subList(start, Math.min(values.size(), start + pageSize));
    }

    private Material fillerMaterial(Menu menu) {
        if (menu == Menu.RANKUP_CONFIRM || menu == Menu.RANKUP_MAX_CONFIRM || menu == Menu.DANGER_CONFIRM
                || menu == Menu.PRESTIGE_CONFIRM) return theme.dangerFiller();
        if (menu == Menu.PRESTIGE) return theme.prestigeFiller();
        if (menu == Menu.SELLING || menu == Menu.GANG_BANK || menu == Menu.GANG_UPGRADES) {
            return theme.economyFiller();
        }
        if (menu == Menu.STATISTICS || menu == Menu.LEADERBOARD || menu == Menu.GANG_STATS
                || menu == Menu.GANG_LEADERBOARDS) return theme.informationFiller();
        if (menu.name().startsWith("ADMIN")) return theme.adminFiller();
        return theme.filler();
    }

    private void play(Player player, org.bukkit.Sound sound) {
        player.playSound(player.getLocation(), sound, theme.soundVolume(), theme.soundPitch());
    }

    private String leaderboardValue(String metric, BigDecimal value) {
        return switch (metric) {
            case "money" -> plugin.numbers().currency(value);
            case "playtime" -> formatDuration(value.longValue());
            case "rank" -> rankName(value.intValue());
            case "prestige" -> prestigeName(value.intValue());
            default -> plugin.numbers().full(value);
        };
    }

    private String rankName(int index) {
        List<RankDefinition> ranks = plugin.rankService().definitions();
        return index >= 0 && index < ranks.size() ? ranks.get(index).displayName() : "unknown";
    }

    private String prestigeName(int value) {
        if (value <= 0) return "none";
        List<PrestigeDefinition> prestiges = plugin.prestigeService().definitions();
        int index = value - 1;
        return index >= 0 && index < prestiges.size() ? prestiges.get(index).displayName() : "unknown";
    }

    private AdminEntry admin(Material material, String title, String description, String command, String action) {
        return new AdminEntry(material, title, description, command, action);
    }

    private long inputTimeoutMillis() {
        return Math.max(30_000L, plugin.config().snapshot().guiSecurity().confirmationTimeoutMillis());
    }

    private static String safeToken(String value) {
        return value == null ? "" : value.replace("|", " ").trim();
    }

    private static String unsafedToken(String value) {
        return value == null ? "" : value;
    }

    private static String shortRevision(String revision) {
        return revision == null || revision.length() < 12 ? String.valueOf(revision) : revision.substring(0, 12);
    }

    private static String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) return String.valueOf(value);
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static Map<Menu, String> defaultTitles() {
        EnumMap<Menu, String> map = new EnumMap<>(Menu.class);
        map.put(Menu.MAIN, "&8RelicPrison &7• &dMain Menu");
        map.put(Menu.MINES, "&8Mines &7• &bWarp Directory");
        map.put(Menu.PROGRESSION, "&8Progression &7• &bRanks");
        map.put(Menu.RANKUP_CONFIRM, "&4&lConfirm Rankup");
        map.put(Menu.RANKUP_MAX_CONFIRM, "&4&lConfirm Rankup Max");
        map.put(Menu.PRESTIGE, "&8Progression &7• &dPrestige");
        map.put(Menu.PRESTIGE_CONFIRM, "&4&lConfirm Prestige");
        map.put(Menu.DANGER_CONFIRM, "&4&lConfirm Dangerous Action");
        map.put(Menu.SELLING, "&8Economy &7• &6Selling");
        map.put(Menu.BOOSTERS, "&8Boosters &7• &dActive Effects");
        map.put(Menu.STATISTICS, "&8Profile &7• &bStatistics");
        map.put(Menu.LEADERBOARD, "&8Statistics &7• &bLeaderboard");
        map.put(Menu.GANG, "&8Gang &7• &dHeadquarters");
        map.put(Menu.GANG_MEMBER, "&8Gang &7• &aMember Profile");
        map.put(Menu.ADMIN, "&8Admin &7• &cControl Center");
        for (Menu menu : Menu.values()) map.putIfAbsent(menu, menu.name().startsWith("GANG")
                ? "&8Gang &7• &dManagement" : "&8Admin &7• &cManagement");
        return Map.copyOf(map);
    }

    private static boolean safeEquals(String first, String second) {
        return java.util.Objects.equals(first, second);
    }

    private static String formatDuration(long seconds) {
        Duration duration = Duration.ofSeconds(Math.max(0L, seconds));
        long d = duration.toDays();
        long h = duration.minusDays(d).toHours();
        long m = duration.minusDays(d).minusHours(h).toMinutes();
        long s = duration.minusDays(d).minusHours(h).minusMinutes(m).toSeconds();
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m " + s + "s";
    }

    private record GuiSession(UUID id, Menu menu, int page, String context, long generation, long openedAt) { }
    private record RankPlan(String targetRank, BigDecimal cost) { }
    private record AdminEntry(Material material, String title, String description, String command, String action) { }
    private record AdminInputSession(Editor editor, Operation operation, String targetId, String property,
                                     String expectedRevision, int page, String search, long expiresAt) { }
    private record ParsedAdminInput(String targetId, String property, String value) { }
    private record BoosterCreate(String id, BigDecimal multiplier, long durationMillis, boolean serverWide,
                                 UUID owner, long startsAt) { }
    private enum GangRankInputType { CREATE, NAME, PRIORITY, COLOR }
    private record GangRankInput(GangRankInputType type, UUID rankId, String name, int priority,
                                 String color, long expiresAt) { }
    private record ConfirmationContext(UUID playerId, Menu menu, String previousRank, String previousPrestige,
                                       String targetRank, String targetPrestige, BigDecimal cost, long expiresAt,
                                       AtomicBoolean submitted) { }
    private record DangerConfirmation(UUID playerId, String title, String description, String executeAction,
                                      String backAction, long expiresAt, AtomicBoolean submitted) { }
    private static final class AdminGuiException extends RuntimeException {
        private AdminGuiException(Throwable cause) { super(cause); }
    }
}
