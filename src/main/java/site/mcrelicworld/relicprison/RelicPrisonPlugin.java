package site.mcrelicworld.relicprison;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import site.mcrelicworld.relicprison.access.MineAccessServiceImpl;
import site.mcrelicworld.relicprison.admin.BackupServiceImpl;
import site.mcrelicworld.relicprison.admin.AuditRepository;
import site.mcrelicworld.relicprison.admin.AuditService;
import site.mcrelicworld.relicprison.admin.AdminGuiEditorService;
import site.mcrelicworld.relicprison.admin.ServerThreadGate;
import site.mcrelicworld.relicprison.admin.CommandDispatchMonitor;
import site.mcrelicworld.relicprison.admin.DataTransferService;
import site.mcrelicworld.relicprison.admin.DiagnosticServiceImpl;
import site.mcrelicworld.relicprison.admin.PerformanceMetrics;
import site.mcrelicworld.relicprison.admin.ValidationService;
import site.mcrelicworld.relicprison.blockevent.BlockEventRepository;
import site.mcrelicworld.relicprison.blockevent.BlockEventService;
import site.mcrelicworld.relicprison.booster.BoosterConfigRepository;
import site.mcrelicworld.relicprison.booster.BoosterItemService;
import site.mcrelicworld.relicprison.booster.BoosterRepository;
import site.mcrelicworld.relicprison.booster.BoosterServiceImpl;
import site.mcrelicworld.relicprison.command.BoosterCommand;
import site.mcrelicworld.relicprison.command.GangChatCommand;
import site.mcrelicworld.relicprison.command.GangCommand;
import site.mcrelicworld.relicprison.command.MiningToggleCommand;
import site.mcrelicworld.relicprison.command.SellCommand;
import site.mcrelicworld.relicprison.api.RelicPrisonApi;
import site.mcrelicworld.relicprison.command.MenuCommand;
import site.mcrelicworld.relicprison.command.MineCommand;
import site.mcrelicworld.relicprison.command.PrestigeCommand;
import site.mcrelicworld.relicprison.command.RankupCommand;
import site.mcrelicworld.relicprison.command.RelicMineCommand;
import site.mcrelicworld.relicprison.command.RelicPrestigeCommand;
import site.mcrelicworld.relicprison.command.RelicPrisonCommand;
import site.mcrelicworld.relicprison.command.RelicRankCommand;
import site.mcrelicworld.relicprison.command.RelicGangCommand;
import site.mcrelicworld.relicprison.config.ConfigManager;
import site.mcrelicworld.relicprison.config.ConfigSnapshot;
import site.mcrelicworld.relicprison.config.IntegrationConfig;
import site.mcrelicworld.relicprison.config.IntegrationAvailabilityValidator;
import site.mcrelicworld.relicprison.config.ReloadValidationException;
import site.mcrelicworld.relicprison.database.DatabaseManager;
import site.mcrelicworld.relicprison.database.PlayerLoadState;
import site.mcrelicworld.relicprison.database.PlayerProfileRepository;
import site.mcrelicworld.relicprison.economy.VaultEconomyAdapter;
import site.mcrelicworld.relicprison.gui.GuiListener;
import site.mcrelicworld.relicprison.gui.MineAdminGui;
import site.mcrelicworld.relicprison.gui.MineGuiConfig;
import site.mcrelicworld.relicprison.gui.PrisonGuiListener;
import site.mcrelicworld.relicprison.gui.PrisonGuiManager;
import site.mcrelicworld.relicprison.gang.GangConfigRepository;
import site.mcrelicworld.relicprison.gang.GangRepository;
import site.mcrelicworld.relicprison.gang.GangService;
import site.mcrelicworld.relicprison.gang.migration.GangMigrationService;
import site.mcrelicworld.relicprison.integration.AdvancedEnchantmentsIntegration;
import site.mcrelicworld.relicprison.integration.CombatTagIntegration;
import site.mcrelicworld.relicprison.integration.ItemsAdderIntegration;
import site.mcrelicworld.relicprison.integration.LuckPermsIntegration;
import site.mcrelicworld.relicprison.integration.WorldEditSelectionProvider;
import site.mcrelicworld.relicprison.integration.WorldGuardIntegration;
import site.mcrelicworld.relicprison.internal.RelicPrisonApiService;
import site.mcrelicworld.relicprison.leaderboard.LeaderboardRewardRepository;
import site.mcrelicworld.relicprison.leaderboard.LeaderboardRewardService;
import site.mcrelicworld.relicprison.lifecycle.PluginLifecycleState;
import site.mcrelicworld.relicprison.listener.BoosterItemListener;
import site.mcrelicworld.relicprison.listener.GangChatListener;
import site.mcrelicworld.relicprison.listener.MineGameplayListener;
import site.mcrelicworld.relicprison.listener.MiningListener;
import site.mcrelicworld.relicprison.listener.PlaceholderCacheListener;
import site.mcrelicworld.relicprison.listener.PlayerDataListener;
import site.mcrelicworld.relicprison.listener.ProgressionJoinListener;
import site.mcrelicworld.relicprison.logging.LogCategory;
import site.mcrelicworld.relicprison.logging.StructuredLogger;
import site.mcrelicworld.relicprison.message.MessageService;
import site.mcrelicworld.relicprison.message.NumberFormatter;
import site.mcrelicworld.relicprison.mining.MiningConfigRepository;
import site.mcrelicworld.relicprison.mining.MiningServiceImpl;
import site.mcrelicworld.relicprison.mining.ToolDurabilityService;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.mine.MineRepository;
import site.mcrelicworld.relicprison.mine.MineServiceImpl;
import site.mcrelicworld.relicprison.mine.MineStructureService;
import site.mcrelicworld.relicprison.mine.PackagedMineResourceStatus;
import site.mcrelicworld.relicprison.placeholder.RelicPrisonExpansion;
import site.mcrelicworld.relicprison.progression.PrestigeConfirmationManager;
import site.mcrelicworld.relicprison.progression.PrestigeDefinition;
import site.mcrelicworld.relicprison.progression.PrestigeRepository;
import site.mcrelicworld.relicprison.progression.PrestigeServiceImpl;
import site.mcrelicworld.relicprison.progression.ProgressionServiceImpl;
import site.mcrelicworld.relicprison.progression.ProgressionTransactionRepository;
import site.mcrelicworld.relicprison.progression.RankDefinition;
import site.mcrelicworld.relicprison.progression.RankRepository;
import site.mcrelicworld.relicprison.progression.RankServiceImpl;
import site.mcrelicworld.relicprison.reward.RewardLedgerRepository;
import site.mcrelicworld.relicprison.reward.RewardLedgerService;
import site.mcrelicworld.relicprison.reset.MineResetServiceImpl;
import site.mcrelicworld.relicprison.reset.MineRuntimeRepository;
import site.mcrelicworld.relicprison.selection.SelectionManager;
import site.mcrelicworld.relicprison.selling.MultiplierServiceImpl;
import site.mcrelicworld.relicprison.selling.SellPriceCatalog;
import site.mcrelicworld.relicprison.selling.SellServiceImpl;
import site.mcrelicworld.relicprison.selling.SellSummaryService;
import site.mcrelicworld.relicprison.selection.WandListener;
import site.mcrelicworld.relicprison.statistics.StatisticsServiceImpl;
import site.mcrelicworld.relicprison.startup.StartupReporter;
import site.mcrelicworld.relicprison.startup.StartupReporter.Integration;
import site.mcrelicworld.relicprison.startup.StartupReporter.StartupReport;
import site.mcrelicworld.relicprison.teleport.MineTeleportService;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class RelicPrisonPlugin extends JavaPlugin {
    private final StructuredLogger structuredLogger = new StructuredLogger(getLogger());
    private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();
    private volatile PluginLifecycleState lifecycleState = PluginLifecycleState.DISABLED;
    private ConfigManager configManager;
    private MessageService messageService;
    private NumberFormatter numberFormatter;
    private MineServiceImpl mineService;
    private RankServiceImpl rankService;
    private PrestigeServiceImpl prestigeService;
    private DatabaseManager databaseManager;
    private PlayerProfileRepository playerProfiles;
    private VaultEconomyAdapter economy;
    private LuckPermsIntegration luckPerms;
    private ProgressionTransactionRepository progressionTransactions;
    private ProgressionServiceImpl progression;
    private MineResetServiceImpl mineResets;
    private MineAccessServiceImpl mineAccess;
    private MineTeleportService mineTeleports;
    private MineStructureService mineStructures;
    private WorldEditSelectionProvider worldEdit;
    private WorldGuardIntegration worldGuard;
    private SelectionManager selections;
    private WandListener wandListener;
    private MineAdminGui mineAdminGui;
    private PrestigeConfirmationManager prestigeConfirmations;
    private BoosterConfigRepository boosterConfigs;
    private BoosterServiceImpl boosterService;
    private BoosterItemService boosterItems;
    private MultiplierServiceImpl multiplierService;
    private SellServiceImpl sellService;
    private SellSummaryService sellSummaries;
    private MiningConfigRepository miningConfigs;
    private MiningServiceImpl miningService;
    private BlockEventService blockEvents;
    private AdvancedEnchantmentsIntegration advancedEnchantments;
    private ItemsAdderIntegration itemsAdder;
    private CombatTagIntegration combatTags;
    private StatisticsServiceImpl statistics;
    private LeaderboardRewardService leaderboardRewards;
    private GangConfigRepository gangConfigs;
    private GangService gangs;
    private GangMigrationService gangMigrations;
    private PrisonGuiManager prisonGuis;
    private RelicPrisonExpansion placeholderExpansion;
    private boolean placeholderRegistered;
    private BackupServiceImpl backups;
    private ValidationService validation;
    private DiagnosticServiceImpl diagnostics;
    private DataTransferService dataTransfer;
    private AuditService auditService;
    private AdminGuiEditorService adminGuiEditors;
    private CommandDispatchMonitor commandDispatchMonitor;
    private RewardLedgerService rewardLedger;
    private final PerformanceMetrics performanceMetrics = new PerformanceMetrics();
    private int autosaveTaskId = -1;
    private long startupStartedNanos;

    @Override public void onEnable() {
        startupStartedNanos = System.nanoTime();
        lifecycleState = PluginLifecycleState.BOOTSTRAPPING;
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) throw new IllegalStateException("Unable to create plugin data folder");
            BackupServiceImpl.applyPendingRestore(getDataFolder().toPath(), getLogger());
            saveDefaultResources();
            warnIfPackagedMineAssetMissing();
            configManager = new ConfigManager(this);
            ConfigSnapshot snapshot = configManager.loadInitial();
            structuredLogger.configure(snapshot.logging());
            messageService = new MessageService(this);
            messageService.load();
            numberFormatter = new NumberFormatter(snapshot.formatting());

            mineService = new MineServiceImpl(this, new MineRepository(this));
            mineService.load();
            rankService = new RankServiceImpl(new RankRepository(this));
            rankService.load(snapshot.progression().rankGroupFormat());
            prestigeService = new PrestigeServiceImpl(new PrestigeRepository(this));
            prestigeService.load(snapshot.progression().prestigeGroupFormat());
            validateReferences();

            economy = new VaultEconomyAdapter(this);
            economy.initialize();
            luckPerms = new LuckPermsIntegration(this);
            luckPerms.initialize();

            registerCommands();

            databaseManager = new DatabaseManager(this, snapshot.storage());
            lifecycleState = PluginLifecycleState.DATABASE_INITIALIZING;
            structuredLogger.info(LogCategory.DATABASE, "Schema initialization started asynchronously.");
            databaseManager.initializeAsync().whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) {
                    failStartup("Database initialization failed", error);
                    return;
                }
                finishEnableAfterDatabase(snapshot);
            }));
        } catch (Exception ex) {
            failStartup("RelicPrison could not start", ex);
        }
    }

    private void finishEnableAfterDatabase(ConfigSnapshot snapshot) {
        lifecycleState = PluginLifecycleState.LOADING_SERVICES;
        try {
            playerProfiles = new PlayerProfileRepository(databaseManager, snapshot.timezone(),
                    snapshot.progression().startingRank(), snapshot.storage().profileLoadTimeoutSeconds());
            statistics = new StatisticsServiceImpl(this, databaseManager);
            rewardLedger = new RewardLedgerService(this, new RewardLedgerRepository(databaseManager));
            leaderboardRewards = new LeaderboardRewardService(this, new LeaderboardRewardRepository(databaseManager));

            itemsAdder = new ItemsAdderIntegration(this);
            itemsAdder.initialize();

            progressionTransactions = new ProgressionTransactionRepository(databaseManager);
            progression = new ProgressionServiceImpl(this, playerProfiles, rankService, prestigeService, economy,
                    luckPerms, progressionTransactions);
            prestigeConfirmations = new PrestigeConfirmationManager();

            boosterConfigs = new BoosterConfigRepository(this);
            boosterConfigs.load();
            boosterService = new BoosterServiceImpl(this, new BoosterRepository(databaseManager), boosterConfigs);
            boosterItems = new BoosterItemService(this, boosterConfigs);
            gangConfigs = new GangConfigRepository(this);
            gangConfigs.load();
            gangs = new GangService(this, new GangRepository(databaseManager), gangConfigs);
            gangMigrations = new GangMigrationService(gangs.repository(), gangConfigs.config());
            multiplierService = new MultiplierServiceImpl(this, boosterService, boosterConfigs);
            sellService = new SellServiceImpl(this, economy, multiplierService, boosterItems, SellPriceCatalog.load(this));
            blockEvents = new BlockEventService(this, new BlockEventRepository(databaseManager));
            miningConfigs = new MiningConfigRepository(this);
            miningConfigs.load();
            sellSummaries = new SellSummaryService(this, miningConfigs.config().sellSummarySeconds());
            sellSummaries.initialize();
            miningService = new MiningServiceImpl(this, miningConfigs, sellService, sellSummaries,
                    new ToolDurabilityService(this, miningConfigs));
            advancedEnchantments = new AdvancedEnchantmentsIntegration(this);
            advancedEnchantments.initialize();
            combatTags = new CombatTagIntegration(this);
            combatTags.initialize();

            mineResets = new MineResetServiceImpl(this, mineService, new MineRuntimeRepository(databaseManager));
            mineAccess = new MineAccessServiceImpl(this, mineService, playerProfiles, rankService, prestigeService);
            miningService.initialize();
            mineTeleports = new MineTeleportService(this, mineService, mineAccess);
            mineStructures = new MineStructureService(this);
            mineStructures.initialize();

            worldEdit = new WorldEditSelectionProvider();
            selections = new SelectionManager(this, worldEdit);
            wandListener = new WandListener(this, selections);
            mineAdminGui = new MineAdminGui(this);
            worldGuard = new WorldGuardIntegration(this);

            backups = new BackupServiceImpl(this);
            backups.initialize();
            validation = new ValidationService(this);
            diagnostics = new DiagnosticServiceImpl(this, validation);
            dataTransfer = new DataTransferService(this);
            dataTransfer.initialize();
            auditService = new AuditService(new AuditRepository(databaseManager));
            adminGuiEditors = new AdminGuiEditorService(getDataFolder().toPath(), this::reloadAdminEditorOnMainThread,
                    this::recordAdminEditorAudit,
                    () -> mineService.mines().stream().map(MineDefinition::id).collect(java.util.stream.Collectors.toSet()),
                    () -> itemsAdder != null && itemsAdder.enabled(),
                    id -> {
                        if (itemsAdder == null) return false;
                        try {
                            return new ServerThreadGate(Bukkit::isPrimaryThread,
                                    task -> Bukkit.getScheduler().runTask(this, task))
                                    .call(() -> itemsAdder.isRegisteredBlock(id));
                        } catch (Exception error) {
                            throw new IllegalStateException("Custom block lookup failed", error);
                        }
                    });
            prisonGuis = new PrisonGuiManager(this, adminGuiEditors);
            prisonGuis.reload();
            commandDispatchMonitor = new CommandDispatchMonitor(this);
            databaseManager.addReconnectListener(this::recoverAfterDatabaseReconnect);

            Bukkit.getPluginManager().registerEvents(wandListener, this);
            Bukkit.getPluginManager().registerEvents(new PlayerDataListener(this), this);
            Bukkit.getPluginManager().registerEvents(new ProgressionJoinListener(this), this);
            Bukkit.getPluginManager().registerEvents(new MineGameplayListener(this, mineAccess, mineResets, mineTeleports), this);
            Bukkit.getPluginManager().registerEvents(new MiningListener(this, miningService), this);
            Bukkit.getPluginManager().registerEvents(new PlaceholderCacheListener(this), this);
            Bukkit.getPluginManager().registerEvents(new BoosterItemListener(this, boosterItems, boosterService), this);
            Bukkit.getPluginManager().registerEvents(new GangChatListener(this), this);
            Bukkit.getPluginManager().registerEvents(mineResets, this);
            Bukkit.getPluginManager().registerEvents(mineStructures, this);
            Bukkit.getPluginManager().registerEvents(new GuiListener(mineAdminGui), this);
            Bukkit.getPluginManager().registerEvents(new PrisonGuiListener(prisonGuis), this);
            Bukkit.getPluginManager().registerEvents(worldGuard, this);

            CompletableFuture<Void> databaseBackedServices = CompletableFuture.allOf(
                    statistics.initializeAsync(),
                    leaderboardRewards.initializeAsync(),
                    blockEvents.initializeAsync(),
                    boosterService.initializeAsync(),
                    gangs.initializeAsync(),
                    mineResets.initializeAsync(),
                    luckPerms.ensureInheritance(rankService.definitions().stream().map(RankDefinition::luckPermsGroup).toList()),
                    luckPerms.ensureInheritance(prestigeService.definitions().stream().map(PrestigeDefinition::luckPermsGroup).toList())
            ).orTimeout(30, TimeUnit.SECONDS);
            databaseBackedServices.whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) {
                    failStartup("Database-backed service initialization failed", error);
                    return;
                }
                activateReadyServices(snapshot);
            }));
        } catch (Exception ex) {
            failStartup("RelicPrison could not finish service loading", ex);
        }
    }

    private void activateReadyServices(ConfigSnapshot snapshot) {
        try {
            statistics.startFlushTask();
            rewardLedger.start();
            boosterService.startExpiryTask();
            mineResets.startSchedulers();
            worldGuard.initialize();
            registerPlaceholderExpansion();

            RelicPrisonApi api = new RelicPrisonApiService(mineService, mineResets, mineAccess, rankService,
                    prestigeService, progression, playerProfiles, sellService, multiplierService, boosterService,
                    miningService, statistics, backups, diagnostics, numberFormatter, getPluginMeta().getVersion());
            Bukkit.getServicesManager().register(RelicPrisonApi.class, api, this, ServicePriority.Normal);

            long saveTicks = snapshot.storage().saveIntervalSeconds() * 20L;
            autosaveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this,
                    () -> playerProfiles.flushDirty().exceptionally(error -> {
                        structuredLogger.severe(LogCategory.DATABASE, "Periodic player save failed: " + rootMessage(error));
                        return null;
                    }), saveTicks, saveTicks);

            lifecycleState = PluginLifecycleState.READY;
            readyFuture.complete(null);
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) loadProfileWhenReady(player);

            reportReady(snapshot);
        } catch (Exception ex) {
            failStartup("RelicPrison could not activate Bukkit-facing services", ex);
        }
    }

    private void reportReady(ConfigSnapshot snapshot) {
        IntegrationConfig integrations = snapshot.integrations();
        boolean combatConfigured = integrations.combatTeleportRestrictionEnabled()
                && !"none".equalsIgnoreCase(integrations.combatProvider());
        List<Integration> statuses = List.of(
                integration("Vault", integrations.vaultEnabled(), installed("Vault"), economy.connected()),
                integration("LuckPerms", integrations.luckPermsEnabled(), installed("LuckPerms"), luckPerms.connected()),
                integration("PlaceholderAPI", integrations.placeholderApiEnabled(), installed("PlaceholderAPI"),
                        placeholderRegistered),
                integration("ItemsAdder", integrations.itemsAdderEnabled(), installed("ItemsAdder"),
                        itemsAdder.connected()),
                integration("AdvancedEnchantments", integrations.advancedEnchantmentsEnabled(),
                        installed("AdvancedEnchantments"), advancedEnchantments.connected()),
                integration("WorldEdit / FAWE", integrations.worldEditFaweEnabled(),
                        installed("WorldEdit", "FastAsyncWorldEdit"), worldEdit.available()),
                integration("WorldGuard", integrations.worldGuardEnabled(), installed("WorldGuard"),
                        worldGuard.available()),
                integration("Combat integration", combatConfigured, combatTags.detected(), combatTags.connected())
        );
        StartupReporter.report(getLogger(), new StartupReport(
                getPluginMeta().getVersion(),
                Bukkit.getName() + " " + Bukkit.getBukkitVersion(),
                Bukkit.getMinecraftVersion(),
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown") + " " + System.getProperty("os.version", "unknown"),
                storageName(snapshot),
                mineService.mines().size(),
                rankService.ranks().size(),
                prestigeService.prestiges().size(),
                statuses,
                System.nanoTime() - startupStartedNanos
        ), snapshot.startup());
    }

    private Integration integration(String name, boolean configured, boolean installed, boolean connected) {
        return new Integration(name, StartupReporter.status(configured, installed, connected));
    }

    private boolean installed(String... names) {
        for (String name : names) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) return true;
        }
        return false;
    }

    private static String storageName(ConfigSnapshot snapshot) {
        String name = snapshot.storage().type().name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private void recoverAfterDatabaseReconnect() {
        structuredLogger.info(LogCategory.DATABASE, "database-reconnected recovery=scheduled");
        if (miningService != null) miningService.recoverIncompleteBulkTransactions();
        if (blockEvents != null) blockEvents.recoverIncompleteTriggers().exceptionally(error -> {
            structuredLogger.severe(LogCategory.MINING, "block-event-reconnect-recovery-failed failure="
                    + rootMessage(error));
            return 0;
        });
        if (leaderboardRewards != null) leaderboardRewards.recoverAsync().exceptionally(error -> {
            structuredLogger.severe(LogCategory.DIAGNOSTIC, "leaderboard-reconnect-recovery-failed failure="
                    + rootMessage(error));
            return null;
        });
        if (rewardLedger != null) rewardLedger.recoverAfterDatabaseReconnect();
        if (boosterService != null) boosterService.reloadPersistentState().exceptionally(error -> {
            structuredLogger.severe(LogCategory.DATABASE, "booster-reconnect-recovery-failed failure="
                    + rootMessage(error));
            return null;
        });
    }

    @Override public void onDisable() {
        lifecycleState = PluginLifecycleState.DISABLED;
        if (!readyFuture.isDone()) {
            readyFuture.completeExceptionally(new IllegalStateException("RelicPrison disabled before becoming ready"));
        }
        if (placeholderExpansion != null && placeholderRegistered) {
            try { placeholderExpansion.unregister(); } catch (RuntimeException ignored) { }
            placeholderRegistered = false;
        }
        if (backups != null) backups.shutdown();
        if (rewardLedger != null) rewardLedger.shutdown();
        if (autosaveTaskId != -1) Bukkit.getScheduler().cancelTask(autosaveTaskId);
        if (mineTeleports != null) mineTeleports.shutdown();
        if (gangs != null) gangs.shutdown();
        if (miningService != null) miningService.shutdown();
        if (sellSummaries != null) sellSummaries.shutdown();
        if (boosterService != null) {
            try {
                CompletableFuture<?>[] pauses = Bukkit.getOnlinePlayers().stream()
                        .map(player -> boosterService.playerQuit(player.getUniqueId()))
                        .toArray(CompletableFuture[]::new);
                awaitFuture(CompletableFuture.allOf(pauses), "pause personal boosters");
            } catch (RuntimeException ex) {
                getLogger().warning("Unable to pause all personal boosters during shutdown: " + rootMessage(ex));
            }
            boosterService.shutdown();
        }
        if (mineStructures != null) mineStructures.shutdown();
        if (mineResets != null) {
            try { mineResets.shutdown(); }
            catch (RuntimeException ex) { getLogger().severe("Final mine runtime save failed: " + rootMessage(ex)); }
        }
        if (selections != null) selections.shutdown();
        if (statistics != null) {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) statistics.playerQuit(player.getUniqueId());
            try { statistics.shutdown(); }
            catch (RuntimeException ex) { getLogger().severe("Final statistics save failed: " + rootMessage(ex)); }
        }
        if (playerProfiles != null) {
            try { awaitFuture(playerProfiles.flushDirty(), "final player save"); }
            catch (RuntimeException ex) { getLogger().severe("Final player save failed: " + rootMessage(ex)); }
        }
        if (databaseManager != null) databaseManager.close();
        Bukkit.getServicesManager().unregisterAll(this);
    }

    private void failStartup(String message, Throwable error) {
        lifecycleState = PluginLifecycleState.DEGRADED;
        Throwable root = root(error);
        getLogger().log(Level.SEVERE, "[RelicPrison][STARTUP] " + message + ": " + rootMessage(root), error);
        if (!readyFuture.isDone()) readyFuture.completeExceptionally(root);
        Bukkit.getPluginManager().disablePlugin(this);
    }

    public boolean isReady() {
        return lifecycleState == PluginLifecycleState.READY;
    }

    public PluginLifecycleState lifecycleState() {
        return lifecycleState;
    }

    public boolean ensureReady(org.bukkit.command.CommandSender sender) {
        if (isReady()) return true;
        sendDataLoading(sender);
        return false;
    }

    public boolean ensureProfileReady(org.bukkit.entity.Player player) {
        if (!isReady() || playerProfiles == null) {
            sendDataLoading(player);
            return false;
        }
        if (playerProfiles.cachedProfile(player.getUniqueId()).isPresent()) return true;
        PlayerLoadState state = playerProfiles.loadState(player.getUniqueId());
        if (state == PlayerLoadState.FAILED) {
            messageService.send(player, "profile-load-failed");
            return false;
        }
        if (state == PlayerLoadState.TIMED_OUT) {
            messageService.send(player, "profile-load-timeout");
            return false;
        }
        sendDataLoading(player);
        return false;
    }

    public void sendDataLoading(org.bukkit.command.CommandSender sender) {
        messageService.send(sender, "database-unavailable");
    }

    public void loadProfileWhenReady(org.bukkit.entity.Player player) {
        if (player == null) return;
        if (isReady() && playerProfiles != null) {
            loadPlayerProfile(player);
            return;
        }
        readyFuture.thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) loadPlayerProfile(player);
        })).exceptionally(error -> null);
    }

    private void loadPlayerProfile(org.bukkit.entity.Player player) {
        playerProfiles.startSession(player.getUniqueId());
        playerProfiles.load(player.getUniqueId(), player.getName())
                .thenAccept(profile -> Bukkit.getScheduler().runTask(this,
                        () -> Bukkit.getPluginManager().callEvent(
                                new site.mcrelicworld.relicprison.api.event.RelicPlayerDataLoadEvent(profile))))
                .exceptionally(error -> {
                    structuredLogger.severe(LogCategory.DATABASE,
                            "Unable to load player profile for " + player.getName() + ": " + rootMessage(error));
                    return null;
                });
    }

    private void awaitFuture(CompletableFuture<?> future, String operation) {
        int timeout = 10;
        if (configManager != null) {
            try {
                timeout = configManager.snapshot().storage().shutdownFlushTimeoutSeconds();
            } catch (RuntimeException ignored) {
                timeout = 10;
            }
        }
        if (Bukkit.isPrimaryThread()) {
            future.orTimeout(timeout, TimeUnit.SECONDS).exceptionally(error -> {
                getLogger().severe("Timed out or failed during " + operation + ": " + rootMessage(error));
                return null;
            });
            return;
        }
        try {
            future.orTimeout(timeout, TimeUnit.SECONDS).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during " + operation, ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new IllegalStateException("Timed out or failed during " + operation, ex);
        }
    }

    private void saveDefaultResources() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder");
        }
        for (String resource : new String[]{
                "config.yml", "storage.yml", "messages.yml", "mines.yml", "ranks.yml", "prestiges.yml",
                "sell-prices.yml", "boosters.yml", "mining.yml", "block-events.yml", "custom-drops.yml", "backups.yml",
                "leaderboards.yml", "leaderboard-rewards.yml",
                "gangs.yml", "guis/gangs.yml",
                "guis/theme.yml", "guis/main.yml", "guis/mines.yml", "guis/progression.yml", "guis/prestige.yml", "guis/selling.yml",
                "guis/boosters.yml", "guis/statistics.yml", "guis/admin.yml"
        }) {
            if (!new java.io.File(getDataFolder(), resource).exists()) saveResource(resource, false);
        }
    }

    private void warnIfPackagedMineAssetMissing() {
        PackagedMineResourceStatus status = packagedMineResourceStatus();
        if (!status.missingApprovedAsset()) return;
        structuredLogger.warning(LogCategory.CONFIG,
                "Approved corrected 33-mine mines.yml source asset is missing from packaged resources; "
                        + "fresh installations remain a release blocker until the exact asset is supplied. "
                        + "mine_count=" + status.mineCount());
    }

    public PackagedMineResourceStatus packagedMineResourceStatus() {
        return PackagedMineResourceStatus.inspect(getResource("mines.yml"));
    }

    private void registerCommands() {
        register("relicprison", new RelicPrisonCommand(this));
        register("relicmine", new RelicMineCommand(this));
        register("mine", new MineCommand(this));
        register("rankup", new RankupCommand(this, false));
        register("rankupmax", new RankupCommand(this, true));
        register("prestige", new PrestigeCommand(this));
        register("relicrank", new RelicRankCommand(this));
        register("relicprestige", new RelicPrestigeCommand(this));
        register("sellall", new SellCommand(this, SellCommand.Mode.ALL));
        register("sellhand", new SellCommand(this, SellCommand.Mode.HAND));
        register("sellvalue", new SellCommand(this, SellCommand.Mode.VALUE));
        register("autosell", new MiningToggleCommand(this, MiningToggleCommand.Toggle.AUTOSELL));
        register("autopickup", new MiningToggleCommand(this, MiningToggleCommand.Toggle.AUTOPICKUP));
        register("autosmelt", new MiningToggleCommand(this, MiningToggleCommand.Toggle.AUTOSMELT));
        register("autoblock", new MiningToggleCommand(this, MiningToggleCommand.Toggle.AUTOBLOCK));
        register("booster", new BoosterCommand(this));
        register("gang", new GangCommand(this));
        register("gangchat", new GangChatCommand(this));
        register("relicgang", new RelicGangCommand(this));
        register("prison", new MenuCommand(this, PrisonGuiManager.Menu.MAIN));
        register("ranks", new MenuCommand(this, PrisonGuiManager.Menu.PROGRESSION));
        register("prestiges", new MenuCommand(this, PrisonGuiManager.Menu.PRESTIGE));
        register("stats", new MenuCommand(this, PrisonGuiManager.Menu.STATISTICS));
        register("leaderboard", new MenuCommand(this, PrisonGuiManager.Menu.LEADERBOARD));
    }

    private void register(String name, Object handler) {
        PluginCommand command = requireCommand(name);
        if (handler instanceof org.bukkit.command.CommandExecutor executor) command.setExecutor(executor);
        if (handler instanceof org.bukkit.command.TabCompleter completer) command.setTabCompleter(completer);
    }

    private PluginCommand requireCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Command missing from plugin.yml: " + name);
        return command;
    }

    public void reloadModule(String requested) throws Exception {
        String module = requested.toLowerCase(Locale.ROOT);
        switch (module) {
            case "config" -> {
                ConfigSnapshot before = configManager.snapshot();
                ConfigSnapshot next = configManager.preview();
                rejectIntegrationFailures(validateIntegrationAvailability(next.integrations()));
                rejectIntegrationFailures(restartOnlyReloadFailures(before, next));
                List<RankDefinition> nextRanks = rankService.previewLoad(next.progression().rankGroupFormat());
                List<PrestigeDefinition> nextPrestiges = prestigeService.previewLoad(next.progression().prestigeGroupFormat());
                validateReferences(next, mineService.mines(), nextRanks, nextPrestiges);
                configManager.apply(next);
                structuredLogger.configure(next.logging());
                rankService.apply(nextRanks);
                prestigeService.apply(nextPrestiges);
                numberFormatter.update(next.formatting());
                playerProfiles.updateStartingRank(next.progression().startingRank());
                synchronizeProgressionDefinitions();
                worldGuard.reconfigure();
                warnMissingLinkedMines();
            }
            case "integrations" -> reloadIntegrations();
            case "messages" -> messageService.load();
            case "mines" -> {
                java.util.Map<String, MineDefinition> next = mineService.previewLoad();
                validateReferences(configManager.snapshot(), next.values(), rankService.definitions(), prestigeService.definitions());
                mineService.applyLoaded(next);
                mineResets.reconcileMines();
                worldGuard.reconfigure();
                warnMissingLinkedMines();
            }
            case "ranks" -> {
                List<RankDefinition> next = rankService.previewLoad(configManager.snapshot().progression().rankGroupFormat());
                validateReferences(configManager.snapshot(), mineService.mines(), next, prestigeService.definitions());
                rankService.apply(next);
                synchronizeProgressionDefinitions();
                warnMissingLinkedMines();
            }
            case "prestiges" -> {
                List<PrestigeDefinition> next = prestigeService.previewLoad(configManager.snapshot().progression().prestigeGroupFormat());
                validateReferences(configManager.snapshot(), mineService.mines(), rankService.definitions(), next);
                prestigeService.apply(next);
                synchronizeProgressionDefinitions();
                warnMissingLinkedMines();
            }
            case "progression" -> {
                List<RankDefinition> nextRanks = rankService.previewLoad(configManager.snapshot().progression().rankGroupFormat());
                List<PrestigeDefinition> nextPrestiges = prestigeService.previewLoad(configManager.snapshot().progression().prestigeGroupFormat());
                validateReferences(configManager.snapshot(), mineService.mines(), nextRanks, nextPrestiges);
                rankService.apply(nextRanks);
                prestigeService.apply(nextPrestiges);
                synchronizeProgressionDefinitions();
                warnMissingLinkedMines();
            }
            case "selling" -> sellService.applyCatalog(sellService.previewCatalog());
            case "boosters" -> {
                BoosterConfigRepository repository = boosterConfigs;
                repository.apply(repository.preview());
                boosterService.reconfigure();
                multiplierService.invalidateAll();
            }
            case "mining" -> {
                miningConfigs.apply(miningConfigs.preview());
                miningService.reloadCustomDrops();
                sellSummaries.reconfigure(miningConfigs.config().sellSummarySeconds());
            }
            case "custom-drops" -> miningService.reloadCustomDrops();
            case "block-events" -> blockEvents.reload();
            case "leaderboards" -> statistics.reloadLeaderboards();
            case "leaderboard-rewards" -> leaderboardRewards.reload();
            case "gangs" -> {
                GangConfigRepository repository = gangConfigs;
                repository.apply(repository.preview());
                gangs.refreshAll();
            }
            case "gui" -> { mineAdminGui.reload(); prisonGuis.reload(); }
            case "backups" -> backups.reconfigure();
            case "all" -> reloadAllAtomic();
            default -> throw new IllegalArgumentException("Unknown reload module: " + requested);
        }
    }

    private void reloadAdminEditorModule(AdminGuiEditorService.Editor editor) throws Exception {
        switch (editor) {
            case RANKS -> reloadModule("ranks");
            case PRESTIGES -> reloadModule("prestiges");
            case SELL_PRICES -> reloadModule("selling");
            case BOOSTERS -> reloadModule("boosters");
            case BLOCK_EVENTS -> reloadModule("block-events");
            case RESET_SETTINGS, MINE_COMPOSITION -> reloadModule("mines");
        }
    }

    private void reloadAdminEditorOnMainThread(AdminGuiEditorService.Editor editor) throws Exception {
        new ServerThreadGate(Bukkit::isPrimaryThread, task -> Bukkit.getScheduler().runTask(this, task))
                .run(() -> reloadAdminEditorModule(editor));
    }

    private void recordAdminEditorAudit(UUID staffId, String staffName, AdminGuiEditorService.Editor editor,
                                        AdminGuiEditorService.Operation operation, String targetId,
                                        String before, String after, boolean success, String reason) {
        if (auditService == null) return;
        auditService.record(staffId, staffName, "admin_gui_edit", editor.name().toLowerCase(Locale.ROOT),
                targetId, before, after, success, reason, "", "operation=" + operation.name().toLowerCase(Locale.ROOT));
    }

    private void reloadIntegrations() throws Exception {
        IntegrationConfig next = configManager.previewIntegrations();
        rejectIntegrationFailures(validateIntegrationAvailability(next));
        ConfigSnapshot before = configManager.snapshot();
        try {
            configManager.applyIntegrations(next);
            itemsAdder.initialize();
            advancedEnchantments.initialize();
            if (combatTags != null) combatTags.initialize();
            worldGuard.reconfigure();
            registerPlaceholderExpansion();
            structuredLogger.info(LogCategory.CONFIG, "Reloaded integrations.yml atomically.");
        } catch (Exception ex) {
            configManager.apply(before);
            try {
                restoreIntegrationRuntime();
            } catch (Exception rollbackError) {
                ex.addSuppressed(rollbackError);
            }
            throw new IllegalStateException("integrations.yml apply failed; previous integration settings restored: "
                    + rootMessage(ex), ex);
        }
    }

    private void reloadAllAtomic() throws Exception {
        java.util.List<String> failures = new java.util.ArrayList<>();
        ConfigSnapshot nextConfig = null;
        java.util.Map<String, String> nextMessages = null;
        java.util.Map<String, MineDefinition> nextMines = null;
        List<RankDefinition> nextRanks = null;
        List<PrestigeDefinition> nextPrestiges = null;
        MineGuiConfig nextMineGui = null;
        site.mcrelicworld.relicprison.gui.PrisonGuiManager.Presentation nextPrisonGui = null;
        SellPriceCatalog nextSellPrices = null;
        site.mcrelicworld.relicprison.booster.BoosterConfig nextBoosterConfig = null;
        site.mcrelicworld.relicprison.mining.MiningConfig nextMiningConfig = null;
        site.mcrelicworld.relicprison.mining.CustomDropCatalog nextCustomDrops = null;
        site.mcrelicworld.relicprison.blockevent.BlockEventCatalog nextBlockEvents = null;
        site.mcrelicworld.relicprison.leaderboard.LeaderboardCatalog nextLeaderboards = null;
        site.mcrelicworld.relicprison.leaderboard.LeaderboardRewardConfig nextLeaderboardRewards = null;

        try { nextConfig = configManager.preview(); } catch (Exception ex) { failures.add("config.yml: " + rootMessage(ex)); }
        try { nextMessages = messageService.preview(); } catch (Exception ex) { failures.add("messages.yml: " + rootMessage(ex)); }
        try { nextMines = mineService.previewLoad(); } catch (Exception ex) { failures.add("mines.yml: " + rootMessage(ex)); }
        if (nextConfig != null) {
            try { nextRanks = rankService.previewLoad(nextConfig.progression().rankGroupFormat()); }
            catch (Exception ex) { failures.add("ranks.yml: " + rootMessage(ex)); }
            try { nextPrestiges = prestigeService.previewLoad(nextConfig.progression().prestigeGroupFormat()); }
            catch (Exception ex) { failures.add("prestiges.yml: " + rootMessage(ex)); }
        }
        try { nextMineGui = mineAdminGui.preview(); } catch (Exception ex) { failures.add("guis/mines.yml: " + rootMessage(ex)); }
        try { nextPrisonGui = prisonGuis.preview(); } catch (Exception ex) { failures.add("player GUI files: " + rootMessage(ex)); }
        try { nextSellPrices = sellService.previewCatalog(); } catch (Exception ex) { failures.add("sell-prices.yml: " + rootMessage(ex)); }
        try { nextBoosterConfig = boosterConfigs.preview(); } catch (Exception ex) { failures.add("boosters.yml: " + rootMessage(ex)); }
        try { nextMiningConfig = miningConfigs.preview(); } catch (Exception ex) { failures.add("mining.yml: " + rootMessage(ex)); }
        try { nextCustomDrops = miningService.previewCustomDrops(); } catch (Exception ex) { failures.add("custom-drops.yml: " + rootMessage(ex)); }
        try { nextBlockEvents = blockEvents.preview(); } catch (Exception ex) { failures.add("block-events.yml: " + rootMessage(ex)); }
        try { nextLeaderboards = site.mcrelicworld.relicprison.leaderboard.LeaderboardCatalog.load(getDataFolder()); }
        catch (Exception ex) { failures.add("leaderboards.yml: " + rootMessage(ex)); }
        try { nextLeaderboardRewards = leaderboardRewards.preview(); }
        catch (Exception ex) { failures.add("leaderboard-rewards.yml: " + rootMessage(ex)); }
        if (nextConfig != null) {
            failures.addAll(validateIntegrationAvailability(nextConfig.integrations()));
            failures.addAll(restartOnlyReloadFailures(configManager.snapshot(), nextConfig));
        }
        if (nextConfig != null && nextMines != null && nextRanks != null && nextPrestiges != null) {
            try { validateReferences(nextConfig, nextMines.values(), nextRanks, nextPrestiges); }
            catch (Exception ex) { failures.add("cross-file: " + rootMessage(ex)); }
        }
        if (!failures.isEmpty()) {
            structuredLogger.warning(LogCategory.CONFIG, "Reload rejected. No live settings were changed.");
            throw new ReloadValidationException(failures);
        }

        ConfigSnapshot beforeConfig = configManager.snapshot();
        java.util.Map<String, String> beforeMessages = messageService.snapshot();
        java.util.Map<String, MineDefinition> beforeMines = mineService.snapshot();
        List<RankDefinition> beforeRanks = rankService.definitions();
        List<PrestigeDefinition> beforePrestiges = prestigeService.definitions();
        MineGuiConfig beforeMineGui = mineAdminGui.config();
        site.mcrelicworld.relicprison.gui.PrisonGuiManager.Presentation beforePrisonGui = prisonGuis.snapshot();
        SellPriceCatalog beforeSellPrices = sellService.catalog();
        site.mcrelicworld.relicprison.booster.BoosterConfig beforeBoosterConfig = boosterConfigs.config();
        site.mcrelicworld.relicprison.mining.MiningConfig beforeMiningConfig = miningConfigs.config();
        site.mcrelicworld.relicprison.mining.CustomDropCatalog beforeCustomDrops = miningService.customDrops();
        site.mcrelicworld.relicprison.blockevent.BlockEventCatalog beforeBlockEvents = blockEvents.catalog();
        site.mcrelicworld.relicprison.leaderboard.LeaderboardCatalog beforeLeaderboards =
                statistics.leaderboardCatalog();
        site.mcrelicworld.relicprison.leaderboard.LeaderboardRewardConfig beforeLeaderboardRewards =
                leaderboardRewards.config();
        try {
            configManager.apply(nextConfig);
            structuredLogger.configure(nextConfig.logging());
            messageService.apply(nextMessages);
            mineService.applyLoaded(nextMines);
            rankService.apply(nextRanks);
            prestigeService.apply(nextPrestiges);
            mineAdminGui.apply(nextMineGui);
            prisonGuis.apply(nextPrisonGui);
            sellService.applyCatalog(nextSellPrices);
            boosterConfigs.apply(nextBoosterConfig);
            miningConfigs.apply(nextMiningConfig);
            miningService.applyCustomDrops(nextCustomDrops);
            blockEvents.apply(nextBlockEvents);
            statistics.applyLeaderboards(nextLeaderboards);
            leaderboardRewards.apply(nextLeaderboardRewards);
            boosterService.reconfigure();
            sellSummaries.reconfigure(nextMiningConfig.sellSummarySeconds());
            itemsAdder.initialize();
            advancedEnchantments.initialize();
            if (combatTags != null) combatTags.initialize();
            backups.reconfigure();
            multiplierService.invalidateAll();
            numberFormatter.update(nextConfig.formatting());
            playerProfiles.updateStartingRank(nextConfig.progression().startingRank());
            mineResets.reconcileMines();
            worldGuard.reconfigure();
            registerPlaceholderExpansion();
            warnMissingLinkedMines();
            synchronizeProgressionDefinitions();
            structuredLogger.info(LogCategory.CONFIG, "Reload all completed atomically.");
        } catch (Exception ex) {
            configManager.apply(beforeConfig);
            structuredLogger.configure(beforeConfig.logging());
            messageService.apply(beforeMessages);
            mineService.applyLoaded(beforeMines);
            rankService.apply(beforeRanks);
            prestigeService.apply(beforePrestiges);
            mineAdminGui.apply(beforeMineGui);
            prisonGuis.apply(beforePrisonGui);
            sellService.applyCatalog(beforeSellPrices);
            boosterConfigs.apply(beforeBoosterConfig);
            miningConfigs.apply(beforeMiningConfig);
            miningService.applyCustomDrops(beforeCustomDrops);
            blockEvents.apply(beforeBlockEvents);
            statistics.applyLeaderboards(beforeLeaderboards);
            leaderboardRewards.apply(beforeLeaderboardRewards);
            numberFormatter.update(beforeConfig.formatting());
            playerProfiles.updateStartingRank(beforeConfig.progression().startingRank());
            try {
                restoreRuntimeAfterFailedReload(beforeMiningConfig);
            } catch (Exception rollbackError) {
                ex.addSuppressed(rollbackError);
            }
            throw new IllegalStateException("Reload apply failed; previous live snapshot restored: "
                    + rootMessage(ex), ex);
        }
    }

    private void rejectIntegrationFailures(List<String> failures) throws ReloadValidationException {
        if (!failures.isEmpty()) throw new ReloadValidationException(failures);
    }

    private List<String> validateIntegrationAvailability(IntegrationConfig integrations) {
        return IntegrationAvailabilityValidator.validate(integrations,
                new IntegrationAvailabilityValidator.RuntimeState(
                        Bukkit.getPluginManager().getPlugin("Vault") != null,
                        economyProviderPresent(),
                        Bukkit.getPluginManager().getPlugin("LuckPerms") != null,
                        Bukkit.getPluginManager().getPlugin("WorldGuard") != null,
                        Bukkit.getPluginManager().getPlugin("WorldEdit") != null,
                        Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null,
                        Bukkit.getPluginManager().getPlugin("ItemsAdder") != null,
                        Bukkit.getPluginManager().getPlugin("AdvancedEnchantments") != null));
    }

    private boolean economyProviderPresent() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            return registration != null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private void registerPlaceholderExpansion() {
        if (placeholderExpansion != null && placeholderRegistered) {
            try { placeholderExpansion.unregister(); } catch (RuntimeException ignored) { }
        }
        placeholderExpansion = null;
        placeholderRegistered = false;
        if (!configManager.snapshot().integrations().placeholderApiEnabled()) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            placeholderExpansion = new RelicPrisonExpansion(this);
            placeholderRegistered = placeholderExpansion.register();
            if (placeholderRegistered) getLogger().info("PlaceholderAPI connected: RelicPrison expansion registered.");
            else getLogger().warning("PlaceholderAPI found, but the RelicPrison expansion could not register.");
        } catch (RuntimeException | LinkageError error) {
            placeholderExpansion = null;
            getLogger().warning("PlaceholderAPI integration could not initialize: " + rootMessage(error));
        }
    }

    private void validateReferences() {
        validateReferences(configManager.snapshot(), mineService.mines(), rankService.definitions(), prestigeService.definitions());
        warnMissingLinkedMines();
    }

    private void validateReferences(ConfigSnapshot snapshot, java.util.Collection<MineDefinition> mines,
                                    List<RankDefinition> ranks, List<PrestigeDefinition> prestiges) {
        java.util.Map<String, RankDefinition> rankIndex = new java.util.HashMap<>();
        ranks.forEach(rank -> rankIndex.put(rank.id(), rank));
        java.util.Map<String, PrestigeDefinition> prestigeIndex = new java.util.HashMap<>();
        prestiges.forEach(prestige -> prestigeIndex.put(prestige.id(), prestige));
        if (!rankIndex.containsKey(snapshot.progression().startingRank())) {
            throw new IllegalStateException("Configured starting rank does not exist: " + snapshot.progression().startingRank());
        }
        if (!rankIndex.get(snapshot.progression().startingRank()).enabled()) {
            throw new IllegalStateException("Configured starting rank is disabled: " + snapshot.progression().startingRank());
        }
        java.util.Set<String> groups = new java.util.HashSet<>();
        for (RankDefinition rank : ranks) {
            if (!groups.add(rank.luckPermsGroup())) {
                throw new IllegalStateException("Duplicate managed LuckPerms group: " + rank.luckPermsGroup());
            }
        }
        for (PrestigeDefinition prestige : prestiges) {
            if (!groups.add(prestige.luckPermsGroup())) {
                throw new IllegalStateException("Duplicate managed LuckPerms group: " + prestige.luckPermsGroup());
            }
        }
        for (RankDefinition rank : ranks) validateProgressionRequirements("Rank " + rank.id(), rank.order(),
                rank.requirements(), rankIndex, prestigeIndex);
        for (PrestigeDefinition prestige : prestiges) validateProgressionRequirements("Prestige " + prestige.id(),
                prestige.order(), prestige.requirements(), rankIndex, prestigeIndex);
        for (MineDefinition mine : mines) {
            if (mine.requiredRank() != null && !rankIndex.containsKey(mine.requiredRank())) {
                throw new IllegalStateException("Mine " + mine.id() + " requires unknown rank " + mine.requiredRank());
            }
            if (mine.requiredPrestige() != null && !prestigeIndex.containsKey(mine.requiredPrestige())) {
                throw new IllegalStateException("Mine " + mine.id() + " requires unknown prestige " + mine.requiredPrestige());
            }
            for (site.mcrelicworld.relicprison.mine.composition.CompositionEntry entry : mine.composition().entries()) {
                if (entry.minimumPrestige() != null && !prestigeIndex.containsKey(entry.minimumPrestige())) {
                    throw new IllegalStateException("Mine " + mine.id() + " composition requires unknown prestige "
                            + entry.minimumPrestige());
                }
            }
        }
    }

    private static void validateProgressionRequirements(String owner, int ownerOrder, List<String> requirements,
                                                        java.util.Map<String, RankDefinition> ranks,
                                                        java.util.Map<String, PrestigeDefinition> prestiges) {
        boolean prestigeOwner = owner.startsWith("Prestige ");
        for (String requirement : requirements) {
            String[] parts = requirement.toLowerCase(Locale.ROOT).split(":", 2);
            if (parts.length != 2) throw new IllegalStateException(owner + " has invalid requirement " + requirement);
            if (parts[0].equals("rank")) {
                RankDefinition rank = ranks.get(parts[1]);
                if (rank == null) throw new IllegalStateException(owner + " requires unknown rank " + parts[1]);
                if (!prestigeOwner && rank.order() >= ownerOrder) throw new IllegalStateException(owner + " has impossible rank requirement " + requirement);
            } else if (parts[0].equals("prestige")) {
                PrestigeDefinition prestige = prestiges.get(parts[1]);
                if (prestige == null) throw new IllegalStateException(owner + " requires unknown prestige " + parts[1]);
                if (prestigeOwner && prestige.order() >= ownerOrder) throw new IllegalStateException(owner + " has cyclic prestige requirement " + requirement);
            } else if (!parts[0].equals("permission")) {
                throw new IllegalStateException(owner + " has unsupported requirement " + requirement);
            }
        }
    }

    private void synchronizeProgressionDefinitions() {
        java.util.concurrent.CompletableFuture<Void> groups = java.util.concurrent.CompletableFuture.allOf(
                luckPerms.ensureInheritance(rankService.definitions().stream().map(RankDefinition::luckPermsGroup).toList()),
                luckPerms.ensureInheritance(prestigeService.definitions().stream().map(PrestigeDefinition::luckPermsGroup).toList()));
        groups.orTimeout(15, TimeUnit.SECONDS).exceptionally(error -> {
            structuredLogger.severe(LogCategory.INTEGRATION,
                    "LuckPerms inheritance synchronization is pending after reload: " + rootMessage(error));
            return null;
        });
        for (site.mcrelicworld.relicprison.database.PlayerProfile profile : playerProfiles.cachedProfiles()) {
            progression.repair(profile.uuid(), "reload", true).exceptionally(error -> {
                getLogger().warning("Progression repair after reload failed for " + profile.uuid() + ": " + rootMessage(error));
                return null;
            });
        }
    }

    private void warnMissingLinkedMines() {
        for (RankDefinition rank : rankService.definitions()) {
            if (rank.mineId() != null && mineService.findMine(rank.mineId()).isEmpty()) {
                getLogger().warning("Rank " + rank.id() + " references missing mine " + rank.mineId());
            }
        }
        for (PrestigeDefinition prestige : prestigeService.definitions()) {
            if (prestige.mineId() != null && mineService.findMine(prestige.mineId()).isEmpty()) {
                getLogger().warning("Prestige " + prestige.id() + " references missing mine " + prestige.mineId());
            }
        }
    }

    private List<String> restartOnlyReloadFailures(ConfigSnapshot before, ConfigSnapshot after) {
        List<String> failures = new java.util.ArrayList<>();
        if (!before.storage().equals(after.storage())) {
            failures.add("config.yml: storage settings changed and require a full restart");
        }
        if (before.resetEngine().runtimeSaveIntervalSeconds() != after.resetEngine().runtimeSaveIntervalSeconds()) {
            failures.add("config.yml: reset-engine.runtime-save-interval-seconds requires a full restart");
        }
        return failures;
    }

    private void restoreIntegrationRuntime() throws Exception {
        Exception failure = null;
        try { itemsAdder.initialize(); } catch (Exception ex) { failure = ex; }
        try { advancedEnchantments.initialize(); } catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        try { if (combatTags != null) combatTags.initialize(); } catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        try { worldGuard.reconfigure(); } catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        try { registerPlaceholderExpansion(); } catch (RuntimeException ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        if (failure != null) throw failure;
    }

    private void restoreRuntimeAfterFailedReload(site.mcrelicworld.relicprison.mining.MiningConfig miningConfig)
            throws Exception {
        Exception failure = null;
        try { boosterService.reconfigure(); } catch (Exception ex) { failure = ex; }
        try { sellSummaries.reconfigure(miningConfig.sellSummarySeconds()); }
        catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        try { restoreIntegrationRuntime(); }
        catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        try { backups.reconfigure(); } catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        try { multiplierService.invalidateAll(); } catch (RuntimeException ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        try { mineResets.reconcileMines(); } catch (RuntimeException ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        if (failure != null) throw failure;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = root(throwable);
        return String.valueOf(current.getMessage());
    }

    private static Throwable root(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    public ConfigManager config() { return configManager; }
    public MessageService messages() { return messageService; }
    public NumberFormatter numbers() { return numberFormatter; }
    public MineServiceImpl mineService() { return mineService; }
    public RankServiceImpl rankService() { return rankService; }
    public PrestigeServiceImpl prestigeService() { return prestigeService; }
    public DatabaseManager database() { return databaseManager; }
    public PlayerProfileRepository playerProfiles() { return playerProfiles; }
    public VaultEconomyAdapter economy() { return economy; }
    public LuckPermsIntegration luckPerms() { return luckPerms; }
    public ProgressionTransactionRepository progressionTransactions() { return progressionTransactions; }
    public ProgressionServiceImpl progression() { return progression; }
    public MineResetServiceImpl mineResets() { return mineResets; }
    public MineAccessServiceImpl mineAccess() { return mineAccess; }
    public MineTeleportService mineTeleports() { return mineTeleports; }
    public MineStructureService mineStructures() { return mineStructures; }
    public WorldEditSelectionProvider worldEdit() { return worldEdit; }
    public WorldGuardIntegration worldGuard() { return worldGuard; }
    public SelectionManager selections() { return selections; }
    public WandListener wandListener() { return wandListener; }
    public MineAdminGui mineAdminGui() { return mineAdminGui; }
    public PrestigeConfirmationManager prestigeConfirmations() { return prestigeConfirmations; }
    public BoosterConfigRepository boosterConfigs() { return boosterConfigs; }
    public BoosterServiceImpl boosterService() { return boosterService; }
    public BoosterItemService boosterItems() { return boosterItems; }
    public MultiplierServiceImpl multiplierService() { return multiplierService; }
    public SellServiceImpl sellService() { return sellService; }
    public MiningConfigRepository miningConfigs() { return miningConfigs; }
    public MiningServiceImpl miningService() { return miningService; }
    public BlockEventService blockEvents() { return blockEvents; }
    public AdvancedEnchantmentsIntegration advancedEnchantments() { return advancedEnchantments; }
    public ItemsAdderIntegration itemsAdder() { return itemsAdder; }
    public CombatTagIntegration combatTags() { return combatTags; }
    public StatisticsServiceImpl statistics() { return statistics; }
    public LeaderboardRewardService leaderboardRewards() { return leaderboardRewards; }
    public GangConfigRepository gangConfigs() { return gangConfigs; }
    public GangService gangs() { return gangs; }
    public GangMigrationService gangMigrations() { return gangMigrations; }
    public PrisonGuiManager prisonGuis() { return prisonGuis; }
    public boolean placeholderRegistered() { return placeholderRegistered; }
    public java.util.Map<String, String> placeholderDiagnostics() {
        return placeholderExpansion == null ? java.util.Map.of() : placeholderExpansion.diagnosticValues();
    }
    public void invalidatePlaceholderCache(java.util.UUID playerId) {
        if (placeholderExpansion != null) placeholderExpansion.invalidate(playerId);
    }
    public void invalidateAllPlaceholderCaches() {
        if (placeholderExpansion != null) placeholderExpansion.invalidateAll();
    }
    public BackupServiceImpl backups() { return backups; }
    public ValidationService validation() { return validation; }
    public DiagnosticServiceImpl diagnostics() { return diagnostics; }
    public DataTransferService dataTransfer() { return dataTransfer; }
    public AuditService audit() { return auditService; }
    public CommandDispatchMonitor commandDispatch() { return commandDispatchMonitor; }
    public RewardLedgerService rewardLedger() { return rewardLedger; }
    public PerformanceMetrics performanceMetrics() { return performanceMetrics; }
    public StructuredLogger structuredLogger() { return structuredLogger; }
}
