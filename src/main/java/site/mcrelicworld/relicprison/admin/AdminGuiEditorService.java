package site.mcrelicworld.relicprison.admin;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import site.mcrelicworld.relicprison.blockevent.BlockEventCatalog;
import site.mcrelicworld.relicprison.booster.BoosterConfigRepository;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.mine.MineRepository;
import site.mcrelicworld.relicprison.mine.composition.MineComposition;
import site.mcrelicworld.relicprison.mine.composition.BlockTypeRef;
import site.mcrelicworld.relicprison.mine.composition.CompositionEntry;
import site.mcrelicworld.relicprison.progression.PrestigeDefinition;
import site.mcrelicworld.relicprison.progression.RankDefinition;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Predicate;

/** File-backed admin editor engine used by GUI workflows and tests. */
public final class AdminGuiEditorService {
    public enum Editor {
        RANKS("ranks.yml"),
        PRESTIGES("prestiges.yml"),
        SELL_PRICES("sell-prices.yml"),
        BOOSTERS("boosters.yml"),
        BLOCK_EVENTS("block-events.yml"),
        RESET_SETTINGS("mines.yml"),
        MINE_COMPOSITION("mines.yml");

        private final String fileName;
        Editor(String fileName) { this.fileName = fileName; }
        public String fileName() { return fileName; }
    }

    public enum Operation { CREATE, SET_PROPERTY, TOGGLE_ENABLED, DELETE, NORMALIZE, CANCEL }

    public interface ReloadHook { void reload(Editor editor) throws Exception; }
    public interface AuditHook {
        void record(UUID staffId, String staffName, Editor editor, Operation operation, String targetId,
                    String before, String after, boolean success, String reason);
    }

    private final Path dataFolder;
    private final ReloadHook reloadHook;
    private final AuditHook auditHook;
    private final Supplier<Set<String>> mineIds;
    private final Supplier<Boolean> itemsAdderAvailable;
    private final Predicate<String> customBlockExists;

    public AdminGuiEditorService(Path dataFolder, ReloadHook reloadHook, AuditHook auditHook,
                                 Supplier<Set<String>> mineIds, Supplier<Boolean> itemsAdderAvailable) {
        this(dataFolder, reloadHook, auditHook, mineIds, itemsAdderAvailable, ignored -> true);
    }

    public AdminGuiEditorService(Path dataFolder, ReloadHook reloadHook, AuditHook auditHook,
                                 Supplier<Set<String>> mineIds, Supplier<Boolean> itemsAdderAvailable,
                                 Predicate<String> customBlockExists) {
        this.dataFolder = dataFolder.toAbsolutePath().normalize();
        this.reloadHook = Objects.requireNonNull(reloadHook);
        this.auditHook = Objects.requireNonNull(auditHook);
        this.mineIds = Objects.requireNonNull(mineIds);
        this.itemsAdderAvailable = Objects.requireNonNull(itemsAdderAvailable);
        this.customBlockExists = Objects.requireNonNull(customBlockExists);
    }

    public EditorSnapshot snapshot(Editor editor, int page, String search) throws Exception {
        Path file = file(editor);
        YamlConfiguration yaml = load(file);
        String needle = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<EntrySnapshot> entries = entries(editor, yaml).stream()
                .filter(entry -> needle.isBlank() || entry.id().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.summary().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(EntrySnapshot::id))
                .toList();
        int pageSize = 36;
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        int from = Math.min(entries.size(), safePage * pageSize);
        int to = Math.min(entries.size(), from + pageSize);
        return new EditorSnapshot(editor, revision(file), safePage, pages, entries.subList(from, to));
    }

    public EntryDetails details(Editor editor, String targetId) throws Exception {
        Path file = file(editor);
        YamlConfiguration yaml = load(file);
        return new EntryDetails(editor, revision(file), targetId, summarize(editor, yaml, targetId),
                detailLines(editor, yaml, targetId), editableProperties(editor, targetId));
    }

    public boolean supportsCreate(Editor editor) {
        return editor == Editor.RANKS || editor == Editor.PRESTIGES || editor == Editor.SELL_PRICES
                || editor == Editor.BLOCK_EVENTS || editor == Editor.BOOSTERS;
    }

    public boolean supportsToggle(Editor editor) {
        return editor == Editor.RANKS || editor == Editor.PRESTIGES || editor == Editor.BLOCK_EVENTS
                || editor == Editor.RESET_SETTINGS;
    }

    public boolean supportsDelete(Editor editor, String targetId) {
        if (editor == Editor.MINE_COMPOSITION || editor == Editor.RESET_SETTINGS) return false;
        return editor != Editor.BOOSTERS || targetId != null && targetId.startsWith("permission:");
    }

    public void recordExternal(EditRequest request, String before, String after, boolean success, String reason) {
        auditHook.record(request.staffId(), request.staffName(), request.editor(), request.operation(),
                request.targetId(), before, after, success, reason);
    }

    public synchronized EditResult apply(EditRequest request) {
        Objects.requireNonNull(request);
        if (request.operation() == Operation.CANCEL) {
            return new EditResult(false, "cancelled", request.expectedRevision(), "", "");
        }
        Path path = file(request.editor());
        try {
            String currentRevision = revision(path);
            if (request.expectedRevision() != null && !request.expectedRevision().isBlank()
                    && !request.expectedRevision().equals(currentRevision)) {
                return new EditResult(false, "stale-edit-conflict", currentRevision, "", "");
            }
            YamlConfiguration yaml = load(path);
            String before = summarize(request.editor(), yaml, request.targetId());
            mutate(request, yaml);
            validate(request.editor(), yaml);
            writeAtomically(path, yaml, request.editor());
            String after = summarize(request.editor(), load(path), request.targetId());
            String nextRevision = revision(path);
            auditHook.record(request.staffId(), request.staffName(), request.editor(), request.operation(),
                    request.targetId(), before, after, true, "gui");
            return new EditResult(true, "saved", nextRevision, before, after);
        } catch (Exception ex) {
            auditHook.record(request.staffId(), request.staffName(), request.editor(), request.operation(),
                    request.targetId(), "", "", false, rootMessage(ex));
            return new EditResult(false, rootMessage(ex), safeRevision(path), "", "");
        }
    }

    private void mutate(EditRequest request, YamlConfiguration yaml) {
        switch (request.editor()) {
            case RANKS -> mutateRank(request, yaml);
            case PRESTIGES -> mutatePrestige(request, yaml);
            case SELL_PRICES -> mutateSellPrice(request, yaml);
            case BOOSTERS -> mutateBooster(request, yaml);
            case BLOCK_EVENTS -> mutateBlockEvent(request, yaml);
            case RESET_SETTINGS -> mutateReset(request, yaml);
            case MINE_COMPOSITION -> mutateComposition(request, yaml);
        }
    }

    private void mutateRank(EditRequest request, YamlConfiguration yaml) {
        String root = "ranks";
        if (request.operation() == Operation.CREATE) {
            String id = RankDefinition.normalize(requiredTarget(request));
            if (yaml.contains(root + "." + id)) throw new IllegalArgumentException("Duplicate rank: " + id);
            int order = maxOrder(yaml.getConfigurationSection(root)) + 1;
            yaml.set(root + "." + id + ".display-name", id.toUpperCase(Locale.ROOT));
            yaml.set(root + "." + id + ".order", order);
            yaml.set(root + "." + id + ".next-cost", decimalText(request.value(), BigDecimal.ZERO));
            yaml.set(root + "." + id + ".sell-multiplier", "1.0");
            yaml.set(root + "." + id + ".luckperms-group", id);
            yaml.set(root + "." + id + ".enabled", false);
            return;
        }
        String path = root + "." + RankDefinition.normalize(requiredTarget(request));
        requireSection(yaml, path);
        if (request.operation() == Operation.DELETE) {
            yaml.set(path, null);
        } else if (request.operation() == Operation.TOGGLE_ENABLED) {
            yaml.set(path + ".enabled", !yaml.getBoolean(path + ".enabled", true));
        } else {
            if (normalizedProperty(request.property()).equals("id")) {
                String nextId = RankDefinition.normalize(request.value());
                renameSection(yaml, path, root + "." + nextId, "rank");
                rewriteRequirementReferences(yaml.getConfigurationSection(root), "rank",
                        RankDefinition.normalize(request.targetId()), nextId);
                return;
            }
            if (normalizedProperty(request.property()).equals("cost")) {
                yaml.set(path + ".next-cost", decimal(request.value(), "cost").stripTrailingZeros().toPlainString());
                return;
            }
            setKnownProperty(yaml, path, request.property(), request.value(),
                    Set.of("display-name", "order", "next-cost", "sell-multiplier", "mine", "luckperms-group",
                            "enabled", "permission", "display-material", "lore", "requirements",
                            "commands.enter", "commands.leave"));
        }
    }

    private void mutatePrestige(EditRequest request, YamlConfiguration yaml) {
        String root = "prestiges";
        if (request.operation() == Operation.CREATE) {
            String id = PrestigeDefinition.normalize(requiredTarget(request));
            if (yaml.contains(root + "." + id)) throw new IllegalArgumentException("Duplicate prestige: " + id);
            int order = maxOrder(yaml.getConfigurationSection(root)) + 1;
            yaml.set(root + "." + id + ".display-name", id);
            yaml.set(root + "." + id + ".order", order);
            yaml.set(root + "." + id + ".cost", decimalText(request.value(), BigDecimal.ZERO));
            yaml.set(root + "." + id + ".sell-multiplier", "1.0");
            yaml.set(root + "." + id + ".rank-cost-multiplier", "1.0");
            yaml.set(root + "." + id + ".luckperms-group", id);
            yaml.set(root + "." + id + ".enabled", false);
            return;
        }
        String path = root + "." + PrestigeDefinition.normalize(requiredTarget(request));
        requireSection(yaml, path);
        if (request.operation() == Operation.DELETE) {
            yaml.set(path, null);
        } else if (request.operation() == Operation.TOGGLE_ENABLED) {
            yaml.set(path + ".enabled", !yaml.getBoolean(path + ".enabled", true));
        } else {
            if (normalizedProperty(request.property()).equals("id")) {
                String nextId = PrestigeDefinition.normalize(request.value());
                renameSection(yaml, path, root + "." + nextId, "prestige");
                rewriteRequirementReferences(yaml.getConfigurationSection(root), "prestige",
                        PrestigeDefinition.normalize(request.targetId()), nextId);
                return;
            }
            setKnownProperty(yaml, path, request.property(), request.value(),
                    Set.of("display-name", "order", "cost", "sell-multiplier", "rank-cost-multiplier", "mine",
                            "luckperms-group", "enabled", "permission", "display-material", "lore",
                            "requirements", "commands.enter", "commands.leave"));
        }
    }

    private void mutateSellPrice(EditRequest request, YamlConfiguration yaml) {
        String target = requiredTarget(request);
        String pricePath = sellPricePath(target);
        if (request.operation() == Operation.DELETE) {
            yaml.set(pricePath, null);
            return;
        }
        if (request.operation() == Operation.CREATE && yaml.contains(pricePath)) {
            throw new IllegalArgumentException("Sell price already exists: " + target);
        }
        BigDecimal price = decimal(request.value(), "price");
        if (price.signum() < 0) throw new IllegalArgumentException("Sell price cannot be negative");
        yaml.set(pricePath, price.stripTrailingZeros().toPlainString());
    }

    private void mutateBooster(EditRequest request, YamlConfiguration yaml) {
        if (request.operation() == Operation.CREATE) {
            String node = requiredTarget(request).toLowerCase(Locale.ROOT);
            if (!node.matches("[a-z0-9_.-]{1,128}")) throw new IllegalArgumentException("Invalid permission node");
            yaml.set("permission-multipliers." + node, decimalText(request.value(), BigDecimal.ONE));
            return;
        }
        if (request.operation() == Operation.DELETE) {
            String target = requiredTarget(request).toLowerCase(Locale.ROOT).replaceFirst("^permission:", "");
            yaml.set("permission-multipliers." + target, null);
            return;
        }
        String property = normalizedProperty(request.property());
        String path = property.startsWith("settings.") || property.startsWith("item.")
                || property.startsWith("permission-multipliers.") ? property : "settings." + property;
        if (Set.of("settings.offline-time-continues", "settings.strength-stacks", "settings.duration-stacks",
                "settings.enabled").contains(path)) {
            yaml.set(path, parseBoolean(request.value()));
        } else if (path.equals("item.lore")) {
            yaml.set(path, splitList(request.value()));
        } else if (path.equals("settings.maximum-duration-seconds") || path.equals("item.custom-model-data")) {
            yaml.set(path, integer(request.value(), path));
        } else if (path.equals("settings.maximum-final-multiplier")
                || path.equals("settings.maximum-booster-multiplier")
                || path.startsWith("permission-multipliers.")) {
            yaml.set(path, decimal(request.value(), path).stripTrailingZeros().toPlainString());
        } else {
            yaml.set(path, request.value());
        }
    }

    private void mutateBlockEvent(EditRequest request, YamlConfiguration yaml) {
        if (request.operation() == Operation.CREATE) {
            String id = normalizeId(requiredTarget(request), "event");
            String path = "events." + id;
            if (yaml.contains(path)) throw new IllegalArgumentException("Duplicate event: " + id);
            yaml.set(path + ".enabled", false);
            yaml.set(path + ".priority", 0);
            yaml.set(path + ".trigger", "chance-per-action");
            yaml.set(path + ".mining-type", "both");
            yaml.set(path + ".chance", 1.0D);
            yaml.set(path + ".cooldown-seconds", 0L);
            yaml.set(path + ".blocks", List.of());
            yaml.set(path + ".mines", List.of());
            yaml.set(path + ".permissions", List.of());
            yaml.set(path + ".per-action-command-limit", 4);
            yaml.set(path + ".per-action-reward-limit", 16);
            yaml.set(path + ".rewards.money", decimalText(request.value(), BigDecimal.ONE));
            return;
        }
        String path = "events." + normalizeId(requiredTarget(request), "event");
        requireSection(yaml, path);
        if (request.operation() == Operation.DELETE) {
            yaml.set(path, null);
        } else if (request.operation() == Operation.TOGGLE_ENABLED) {
            boolean next = !yaml.getBoolean(path + ".enabled", true);
            if (next && !hasReward(yaml.getConfigurationSection(path + ".rewards"))) {
                throw new IllegalArgumentException("Cannot enable an event with no valid reward");
            }
            yaml.set(path + ".enabled", next);
        } else {
            if (normalizedProperty(request.property()).equals("id")) {
                renameSection(yaml, path, "events." + normalizeId(request.value(), "event"), "event");
                return;
            }
            setKnownProperty(yaml, path, request.property(), request.value(),
                    Set.of("display-name", "enabled", "priority", "trigger", "mining-type", "chance",
                            "cooldown-seconds", "every-x-blocks", "daily-target", "blocks", "mines",
                            "custom-blocks", "minimum-rank", "minimum-prestige", "permissions",
                            "per-action-command-limit", "per-action-reward-limit", "rewards.money",
                            "rewards.items", "rewards.commands", "rewards.announcements",
                            "rewards.booster.multiplier", "rewards.booster.duration-seconds",
                            "rewards.booster.server-wide", "rewards.experience"));
        }
    }

    private void mutateReset(EditRequest request, YamlConfiguration yaml) {
        String path = minePath(request) + ".reset";
        if (request.operation() == Operation.DELETE) {
            yaml.set(path, null);
            return;
        }
        if (request.operation() == Operation.TOGGLE_ENABLED) {
            yaml.set(path + ".enabled", !yaml.getBoolean(path + ".enabled", true));
            return;
        }
        setKnownProperty(yaml, path, request.property(), request.value(),
                Set.of("timed-enabled", "interval-seconds", "percentage-enabled", "mined-percentage",
                        "countdown-seconds", "warning-seconds", "notification-scope", "notification-radius", "evacuate-players",
                        "order", "commands.before", "commands.start", "commands.complete", "commands.failed",
                        "retry-count", "retry-delay-seconds", "recount-behavior", "teleport-destination",
                        "require-outside-destination", "enabled"));
    }

    private void mutateComposition(EditRequest request, YamlConfiguration yaml) {
        String minePath = minePath(request);
        ConfigurationSection mine = yaml.getConfigurationSection(minePath);
        List<Map<?, ?>> existing = mine == null ? List.of() : mine.getMapList("composition");
        MineComposition composition = MineRepository.readDefinition(requiredTarget(request), mine).composition();
        if (request.operation() == Operation.NORMALIZE) {
            writeComposition(yaml, minePath, composition.normalized());
            return;
        }
        if (request.operation() == Operation.DELETE) {
            writeComposition(yaml, minePath, composition.without(requiredProperty(request)));
            return;
        }
        String block = requiredProperty(request);
        CompositionInput input = compositionInput(block, request.value());
        if (input.block().toLowerCase(Locale.ROOT).startsWith("itemsadder:") && !itemsAdderAvailable.get()) {
            throw new IllegalArgumentException("Custom block provider is unavailable");
        }
        List<CompositionEntry> updated = new ArrayList<>(composition.entries());
        BlockTypeRef reference = BlockTypeRef.parse(input.block());
        if (reference.kind() == BlockTypeRef.Kind.ITEMSADDER && !customBlockExists.test(reference.id())) {
            throw new IllegalArgumentException("Unknown custom block: " + reference.id());
        }
        CompositionEntry replacement = new CompositionEntry(reference, input.weight(), input.minimumPrestige(),
                input.properties(), input.explicitAir());
        boolean replaced = false;
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).block().qualifiedId().equalsIgnoreCase(reference.qualifiedId())) {
                updated.set(index, replacement);
                replaced = true;
                break;
            }
        }
        if (!replaced) updated.add(replacement);
        writeComposition(yaml, minePath, new MineComposition(updated));
    }

    private void validate(Editor editor, YamlConfiguration yaml) {
        switch (editor) {
            case RANKS -> validateRanks(yaml);
            case PRESTIGES -> validatePrestiges(yaml);
            case SELL_PRICES -> validateSellPrices(yaml);
            case BOOSTERS -> validateBoosters(yaml);
            case BLOCK_EVENTS -> validateBlockEvents(yaml);
            case RESET_SETTINGS, MINE_COMPOSITION -> validateMines(yaml);
        }
    }

    private void validateRanks(YamlConfiguration yaml) {
        ConfigurationSection root = requireRoot(yaml, "ranks");
        Set<String> ids = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        Set<String> mines = mineIds.get();
        List<RankDefinition> definitions = new ArrayList<>();
        int natural = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) throw new IllegalArgumentException("ranks." + key + " must be a section");
            String id = RankDefinition.normalize(key);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate rank: " + id);
            int order = section.contains("order") ? section.getInt("order") : natural;
            if (!orders.add(order)) throw new IllegalArgumentException("Duplicate rank order: " + order);
            String mine = blankToNull(section.getString("mine"));
            if (mine != null && !mines.contains(mine.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Rank " + id + " references missing mine " + mine);
            }
            definitions.add(new RankDefinition(id, section.getString("display-name", id.toUpperCase(Locale.ROOT)),
                    order, decimal(section.get("next-cost"), "ranks." + id + ".next-cost"),
                    decimal(section.get("sell-multiplier", "1.0"), "ranks." + id + ".sell-multiplier"),
                    mine, section.getString("luckperms-group", id), section.getStringList("commands.enter"),
                    section.getStringList("commands.leave"), section.getBoolean("enabled", true),
                    blankToNull(section.getString("permission")), displayMaterial(section, "EXPERIENCE_BOTTLE"),
                    section.getStringList("lore"), section.getStringList("requirements")));
            natural++;
        }
        definitions.sort(Comparator.comparingInt(RankDefinition::order));
        if (definitions.size() < 2) throw new IllegalArgumentException("At least two ranks are required");
        List<RankDefinition> enabled = definitions.stream().filter(RankDefinition::enabled).toList();
        if (enabled.size() < 2) throw new IllegalArgumentException("At least two enabled ranks are required");
        if (enabled.getLast().nextCost().signum() != 0) {
            throw new IllegalArgumentException("The final rank must have next-cost 0");
        }
        validateRankRequirements(definitions, configurationIds("prestiges.yml", "prestiges"));
    }

    private void validatePrestiges(YamlConfiguration yaml) {
        ConfigurationSection root = requireRoot(yaml, "prestiges");
        Set<String> ids = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        Set<String> mines = mineIds.get();
        int natural = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) throw new IllegalArgumentException("prestiges." + key + " must be a section");
            String id = PrestigeDefinition.normalize(key);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate prestige: " + id);
            int order = section.contains("order") ? section.getInt("order") : natural;
            if (!orders.add(order)) throw new IllegalArgumentException("Duplicate prestige order: " + order);
            String mine = blankToNull(section.getString("mine"));
            if (mine != null && !mines.contains(mine.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Prestige " + id + " references missing mine " + mine);
            }
            new PrestigeDefinition(id, section.getString("display-name", id), order,
                    decimal(section.get("cost"), "prestiges." + id + ".cost"),
                    decimal(section.get("sell-multiplier"), "prestiges." + id + ".sell-multiplier"),
                    decimal(section.get("rank-cost-multiplier"), "prestiges." + id + ".rank-cost-multiplier"),
                    mine, section.getString("luckperms-group", id), section.getStringList("commands.enter"),
                    section.getStringList("commands.leave"), section.getBoolean("enabled", true),
                    blankToNull(section.getString("permission")), displayMaterial(section, "NETHER_STAR"),
                    section.getStringList("lore"), section.getStringList("requirements"));
            natural++;
        }
        if (root.getKeys(false).stream().map(root::getConfigurationSection)
                .noneMatch(section -> section != null && section.getBoolean("enabled", true))) {
            throw new IllegalArgumentException("At least one prestige must be enabled");
        }
        validatePrestigeRequirements(root, configurationIds("ranks.yml", "ranks"));
    }

    private void validateSellPrices(YamlConfiguration yaml) {
        ConfigurationSection prices = requireRoot(yaml, "prices");
        for (String key : prices.getKeys(false)) {
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (material == null || !material.isItem()) throw new IllegalArgumentException("Unknown sell material: " + key);
            nonnegativeFinite(prices.get(key), "prices." + key);
        }
        ConfigurationSection custom = yaml.getConfigurationSection("custom-prices");
        if (custom != null) validateCustomPriceSection(custom, "custom-prices");
    }

    private void validateCustomPriceSection(ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                validateCustomPriceSection(nested, path + "." + key);
                continue;
            }
            String namespace = path.substring("custom-prices".length()).replaceFirst("^\\.", "");
            if (namespace.isBlank()) {
                throw new IllegalArgumentException("Custom sell price must use namespace:id");
            }
            nonnegativeFinite(value, path + "." + key);
            if (!itemsAdderAvailable.get()) throw new IllegalArgumentException("Custom sell provider is unavailable");
            String id = namespace + ":" + key;
            validateNamespacedId(id, "Custom sell target");
            if (!customBlockExists.test(id)) throw new IllegalArgumentException("Unknown custom block: " + id);
        }
    }

    private void validateBoosters(YamlConfiguration yaml) {
        BigDecimal maxFinal = decimal(yaml.get("settings.maximum-final-multiplier", "100"), "settings.maximum-final-multiplier");
        BigDecimal maxBooster = decimal(yaml.get("settings.maximum-booster-multiplier", "25"), "settings.maximum-booster-multiplier");
        if (maxFinal.signum() <= 0 || maxBooster.signum() <= 0) throw new IllegalArgumentException("Multiplier limits must be positive");
        long duration = yaml.getLong("settings.maximum-duration-seconds", 1L);
        if (duration <= 0) throw new IllegalArgumentException("Maximum booster duration must be positive");
        Duration.ofSeconds(duration);
        Material material = Material.matchMaterial(yaml.getString("item.material", "NETHER_STAR"));
        if (material == null || !material.isItem()) throw new IllegalArgumentException("Invalid booster item material");
        BoosterConfigRepository.permissionMultipliers(yaml.getConfigurationSection("permission-multipliers"));
    }

    private void validateBlockEvents(YamlConfiguration yaml) {
        Path temporaryFolder = null;
        try {
            temporaryFolder = Files.createTempDirectory("rp-block-events-validate");
            Path temporary = temporaryFolder.resolve("block-events.yml");
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            BlockEventCatalog.load(temporaryFolder.toFile());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid block-events.yml: " + rootMessage(ex), ex);
        } finally {
            if (temporaryFolder != null) deleteTree(temporaryFolder);
        }
        ConfigurationSection events = yaml.getConfigurationSection("events");
        if (events == null) return;
        for (String id : events.getKeys(false)) {
            ConfigurationSection section = events.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            if (!hasReward(section.getConfigurationSection("rewards")))
                throw new IllegalArgumentException("Enabled Block Event has no reward: " + id);
            String trigger = section.getString("trigger", "chance-per-action").toLowerCase(Locale.ROOT);
            if (trigger.startsWith("chance-") && doubleNumber(section.get("chance", 0), "chance") <= 0)
                throw new IllegalArgumentException("Enabled chance event must have chance greater than zero: " + id);
            double chance = doubleNumber(section.get("chance", 0), "chance");
            if (chance < 0 || chance > 100) throw new IllegalArgumentException("Block Event chance must be 0-1 or 0-100 percent: " + id);
            if (section.getLong("cooldown-seconds", 0L) < 0) throw new IllegalArgumentException("Cooldown cannot be negative: " + id);
            if (section.getInt("per-action-command-limit", 16) < 0
                    || section.getInt("per-action-reward-limit", 64) <= 0) {
                throw new IllegalArgumentException("Block Event limits are invalid: " + id);
            }
            if (trigger.equals("every-x-blocks") && section.getLong("every-x-blocks", 0L) <= 0)
                throw new IllegalArgumentException("every-x-blocks must be positive: " + id);
            if (trigger.equals("daily-target") && section.getLong("daily-target", 0L) <= 0)
                throw new IllegalArgumentException("daily-target must be positive: " + id);
            for (String mine : section.getStringList("mines")) {
                if (!mineIds.get().contains(mine.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Block Event references missing mine " + mine);
            }
            if (!section.getStringList("custom-blocks").isEmpty() && !itemsAdderAvailable.get())
                throw new IllegalArgumentException("Block Event custom-block provider is unavailable");
            for (String customBlock : section.getStringList("custom-blocks")) {
                validateNamespacedId(customBlock, "Block Event custom block");
                if (!customBlockExists.test(customBlock)) {
                    throw new IllegalArgumentException("Unknown custom block: " + customBlock);
                }
            }
            for (String permission : section.getStringList("permissions")) validatePermission(permission);
            String minimumRank = blankToNull(section.getString("minimum-rank"));
            if (minimumRank != null && !configurationIds("ranks.yml", "ranks").contains(minimumRank.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Block Event references missing rank " + minimumRank);
            }
            String minimumPrestige = blankToNull(section.getString("minimum-prestige"));
            if (minimumPrestige != null && !configurationIds("prestiges.yml", "prestiges")
                    .contains(minimumPrestige.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Block Event references missing prestige " + minimumPrestige);
            }
            ConfigurationSection rewards = section.getConfigurationSection("rewards");
            if (rewards != null && decimal(rewards.get("money", "0"), "rewards.money").signum() < 0)
                throw new IllegalArgumentException("Block Event money reward cannot be negative");
            if (rewards != null) {
                for (Map<?, ?> item : rewards.getMapList("items")) {
                    String itemId = String.valueOf(item.get("id"));
                    if (itemId.contains(":")) {
                        validateNamespacedId(itemId, "Block Event item");
                        if (!itemsAdderAvailable.get()) throw new IllegalArgumentException(
                                "Block Event item provider is unavailable");
                    } else {
                        Material material = Material.matchMaterial(itemId);
                        if (material == null || !material.isItem()) throw new IllegalArgumentException(
                                "Invalid Block Event item " + itemId);
                    }
                    Object amount = item.get("amount");
                    if (amount != null && integer(String.valueOf(amount), "reward item amount") <= 0) {
                        throw new IllegalArgumentException("Block Event item amount must be positive");
                    }
                }
                if (rewards.getInt("xp", rewards.getInt("experience", 0)) < 0) {
                    throw new IllegalArgumentException("Block Event XP reward cannot be negative");
                }
                ConfigurationSection booster = rewards.getConfigurationSection("booster");
                if (booster != null && (decimal(booster.get("multiplier", "0"), "booster.multiplier").signum() <= 0
                        || booster.getLong("duration-seconds", 0L) <= 0)) {
                    throw new IllegalArgumentException("Block Event booster reward is invalid");
                }
            }
        }
    }

    private void validateMines(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("mines");
        if (root == null) return;
        Set<String> ids = new LinkedHashSet<>();
        for (String key : root.getKeys(false)) {
            MineDefinition mine = MineRepository.readDefinition(key, root.getConfigurationSection(key));
            if (!ids.add(mine.id())) throw new IllegalArgumentException("Duplicate mine: " + mine.id());
        }
    }

    private List<EntrySnapshot> entries(Editor editor, YamlConfiguration yaml) {
        return switch (editor) {
            case RANKS -> sectionEntries(yaml, "ranks", "display-name");
            case PRESTIGES -> sectionEntries(yaml, "prestiges", "display-name");
            case SELL_PRICES -> sellEntries(yaml);
            case BOOSTERS -> boosterEntries(yaml);
            case BLOCK_EVENTS -> sectionEntries(yaml, "events", "trigger");
            case RESET_SETTINGS -> sectionEntries(yaml, "mines", "reset.interval-seconds");
            case MINE_COMPOSITION -> sectionEntries(yaml, "mines", "composition");
        };
    }

    private List<EntrySnapshot> sectionEntries(YamlConfiguration yaml, String rootPath, String summaryKey) {
        ConfigurationSection root = yaml.getConfigurationSection(rootPath);
        if (root == null) return List.of();
        List<EntrySnapshot> output = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            Object summary = section == null ? null : section.get(summaryKey);
            output.add(new EntrySnapshot(key.toLowerCase(Locale.ROOT), summary == null ? rootPath : String.valueOf(summary)));
        }
        return output;
    }

    private List<EntrySnapshot> sellEntries(YamlConfiguration yaml) {
        List<EntrySnapshot> output = new ArrayList<>();
        ConfigurationSection prices = yaml.getConfigurationSection("prices");
        if (prices != null) {
            for (String key : prices.getKeys(false)) output.add(new EntrySnapshot(key, "vanilla " + prices.get(key)));
        }
        ConfigurationSection custom = yaml.getConfigurationSection("custom-prices");
        if (custom != null) collectCustomEntries(custom, "", output);
        return output;
    }

    private void collectCustomEntries(ConfigurationSection section, String prefix, List<EntrySnapshot> output) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            String path = prefix.isBlank() ? key : prefix + "." + key;
            if (value instanceof ConfigurationSection nested) collectCustomEntries(nested, path, output);
            else output.add(new EntrySnapshot(path.replace('.', ':'), "custom " + value));
        }
    }

    private List<EntrySnapshot> boosterEntries(YamlConfiguration yaml) {
        List<EntrySnapshot> output = new ArrayList<>();
        output.add(new EntrySnapshot("settings", "max x" + yaml.getString("settings.maximum-final-multiplier", "100")));
        output.add(new EntrySnapshot("item", yaml.getString("item.material", "NETHER_STAR")));
        ConfigurationSection permissions = yaml.getConfigurationSection("permission-multipliers");
        if (permissions != null) {
            for (String key : permissions.getKeys(true)) {
                if (!(permissions.get(key) instanceof ConfigurationSection)) {
                    output.add(new EntrySnapshot("permission:" + key, String.valueOf(permissions.get(key))));
                }
            }
        }
        return output;
    }

    private List<String> detailLines(Editor editor, YamlConfiguration yaml, String targetId) {
        List<String> lines = new ArrayList<>();
        switch (editor) {
            case RANKS -> flatten(yaml.getConfigurationSection("ranks." + RankDefinition.normalize(targetId)), "", lines);
            case PRESTIGES -> flatten(yaml.getConfigurationSection("prestiges." + PrestigeDefinition.normalize(targetId)), "", lines);
            case SELL_PRICES -> lines.add(sellPricePath(targetId) + "=" + yaml.getString(sellPricePath(targetId), "<unset>"));
            case BOOSTERS -> {
                if ("settings".equalsIgnoreCase(targetId)) flatten(yaml.getConfigurationSection("settings"), "settings.", lines);
                else if ("item".equalsIgnoreCase(targetId)) flatten(yaml.getConfigurationSection("item"), "item.", lines);
                else lines.add("permission-multipliers." + targetId.replace("permission:", "") + "="
                        + yaml.getString("permission-multipliers." + targetId.replace("permission:", ""), "<unset>"));
            }
            case BLOCK_EVENTS -> flatten(yaml.getConfigurationSection("events." + normalizeId(targetId, "event")), "", lines);
            case RESET_SETTINGS -> flatten(yaml.getConfigurationSection(minePath(new EditRequest(editor, Operation.SET_PROPERTY,
                    targetId, "", "", "", null, "")) + ".reset"), "reset.", lines);
            case MINE_COMPOSITION -> {
                String path = minePath(new EditRequest(editor, Operation.SET_PROPERTY,
                        targetId, "", "", "", null, ""));
                ConfigurationSection mine = yaml.getConfigurationSection(path);
                if (mine != null) {
                    MineComposition composition = MineRepository.readDefinition(targetId, mine).composition();
                    lines.add("total-weight=" + composition.totalWeight());
                    for (CompositionEntry entry : composition.entries()) {
                        lines.add(entry.block().qualifiedId() + " weight=" + entry.weight()
                                + (entry.minimumPrestige() == null ? "" : " minimum-prestige=" + entry.minimumPrestige())
                                + (entry.explicitAir() ? " allow-air=true" : "")
                                + (entry.properties().isEmpty() ? "" : " properties=" + entry.properties()));
                    }
                }
            }
        }
        return lines.stream().limit(18).toList();
    }

    private static void flatten(ConfigurationSection section, String prefix, List<String> lines) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = prefix + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                flatten(child, path + ".", lines);
            } else {
                lines.add(path + "=" + String.valueOf(value));
            }
        }
    }

    private static List<String> editableProperties(Editor editor, String targetId) {
        return switch (editor) {
            case RANKS -> List.of("id", "display-name", "order", "cost", "sell-multiplier", "mine",
                    "luckperms-group", "enabled", "permission", "display-material", "lore", "requirements",
                    "commands.enter", "commands.leave");
            case PRESTIGES -> List.of("id", "display-name", "order", "cost", "sell-multiplier",
                    "rank-cost-multiplier", "mine", "luckperms-group", "enabled", "permission", "display-material",
                    "lore", "requirements", "commands.enter", "commands.leave");
            case SELL_PRICES -> List.of("price");
            case BOOSTERS -> {
                if ("item".equalsIgnoreCase(targetId)) yield List.of("item.material", "item.name", "item.lore",
                        "item.custom-model-data");
                if (targetId != null && targetId.startsWith("permission:")) yield List.of(
                        "permission-multipliers." + targetId.substring("permission:".length()));
                yield List.of("maximum-final-multiplier", "maximum-booster-multiplier",
                        "maximum-duration-seconds", "offline-time-continues", "strength-stacks", "duration-stacks");
            }
            case BLOCK_EVENTS -> List.of("id", "display-name", "priority", "trigger", "mining-type", "chance",
                    "cooldown-seconds", "every-x-blocks", "daily-target", "blocks", "mines", "custom-blocks",
                    "minimum-rank", "minimum-prestige", "permissions",
                    "per-action-command-limit", "per-action-reward-limit", "rewards.money", "rewards.items",
                    "rewards.commands", "rewards.announcements", "rewards.experience",
                    "rewards.booster.multiplier", "rewards.booster.duration-seconds",
                    "rewards.booster.server-wide", "enabled");
            case RESET_SETTINGS -> List.of("timed-enabled", "interval-seconds", "percentage-enabled",
                    "mined-percentage", "countdown-seconds", "warning-seconds", "notification-scope", "notification-radius",
                    "evacuate-players", "order", "commands.before", "commands.start", "commands.complete",
                    "commands.failed", "retry-count", "retry-delay-seconds", "recount-behavior",
                    "teleport-destination", "require-outside-destination", "enabled");
            case MINE_COMPOSITION -> List.of("entry");
        };
    }

    private void writeAtomically(Path path, YamlConfiguration yaml, Editor editor) throws Exception {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        Path rollback = path.resolveSibling(path.getFileName() + ".rollback");
        byte[] bytes = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        if (Files.exists(path)) {
            Files.copy(path, rollback, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel channel = FileChannel.open(rollback, StandardOpenOption.READ)) { channel.force(true); }
        }
        try {
            moveReplace(temporary, path);
            reloadHook.reload(editor);
        } catch (Exception ex) {
            if (Files.exists(rollback)) {
                Path restore = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".restore");
                try {
                    Files.copy(rollback, restore, StandardCopyOption.REPLACE_EXISTING);
                    try (FileChannel channel = FileChannel.open(restore, StandardOpenOption.READ)) { channel.force(true); }
                    moveReplace(restore, path);
                } finally {
                    Files.deleteIfExists(restore);
                }
                try { reloadHook.reload(editor); }
                catch (Exception rollbackError) { ex.addSuppressed(rollbackError); }
            }
            throw ex;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveReplace(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path file(Editor editor) { return dataFolder.resolve(editor.fileName()).normalize(); }

    private static YamlConfiguration load(Path file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        return yaml;
    }

    private static String revision(Path file) throws Exception {
        if (!Files.exists(file)) return "missing";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
    }

    private static String safeRevision(Path file) {
        try { return revision(file); }
        catch (Exception ex) { return "unavailable"; }
    }

    private static String summarize(Editor editor, YamlConfiguration yaml, String targetId) {
        if (targetId == null || targetId.isBlank()) return editor.name();
        String root = switch (editor) {
            case RANKS -> "ranks";
            case PRESTIGES -> "prestiges";
            case BLOCK_EVENTS -> "events";
            case RESET_SETTINGS, MINE_COMPOSITION -> "mines";
            default -> "";
        };
        if (root.isBlank()) return targetId;
        Object value = yaml.get(root + "." + targetId.toLowerCase(Locale.ROOT));
        return value == null ? "missing:" + targetId : targetId + "=" + value;
    }

    private static void setKnownProperty(YamlConfiguration yaml, String basePath, String property, String value,
                                         Set<String> allowed) {
        String normalized = normalizedProperty(property);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("Unsupported property: " + property);
        String path = basePath + "." + normalized;
        if (normalized.endsWith("enabled") || normalized.equals("enabled") || normalized.endsWith("server-wide")
                || normalized.equals("timed-enabled") || normalized.equals("percentage-enabled")
                || normalized.equals("evacuate-players") || normalized.equals("require-outside-destination")) {
            yaml.set(path, parseBoolean(value));
        } else if (normalized.equals("order") || normalized.equals("priority")
                || normalized.endsWith("limit") || normalized.endsWith("seconds")
                || normalized.equals("notification-radius") || normalized.equals("every-x-blocks")
                || normalized.equals("daily-target") || normalized.equals("rewards.experience")
                || normalized.equals("retry-count") || normalized.equals("countdown-seconds")) {
            yaml.set(path, integer(value, normalized));
        } else if (normalized.equals("warning-seconds")) {
            List<Integer> warnings = splitList(value).stream().map(item -> integer(item, "warning-seconds"))
                    .peek(item -> { if (item < 0) throw new IllegalArgumentException("Warnings cannot be negative"); })
                    .distinct().sorted(Comparator.reverseOrder()).toList();
            yaml.set(path, warnings);
        } else if (normalized.equals("lore") || normalized.equals("requirements")
                || normalized.equals("permissions") || normalized.equals("blocks") || normalized.equals("mines")
                || normalized.equals("custom-blocks")
                || normalized.startsWith("commands.") || normalized.startsWith("rewards.commands")
                || normalized.startsWith("rewards.announcements")) {
            yaml.set(path, splitList(value));
        } else if (normalized.equals("rewards.items")) {
            yaml.set(path, parseItemRewards(value));
        } else if (normalized.equals("mined-percentage")) {
            yaml.set(path, doubleNumber(value, normalized));
        } else if (normalized.contains("cost") || normalized.contains("multiplier")
                || normalized.equals("chance")
                || normalized.equals("rewards.money") || normalized.equals("rewards.experience")) {
            yaml.set(path, decimal(value, normalized).stripTrailingZeros().toPlainString());
        } else {
            yaml.set(path, value == null || value.equalsIgnoreCase("null") || value.isBlank() ? null : value);
        }
    }

    private static List<Map<String, Object>> parseItemRewards(String value) {
        List<Map<String, Object>> output = new ArrayList<>();
        for (String token : splitList(value)) {
            int separator = token.lastIndexOf(':');
            String item = token.trim();
            int amount = 1;
            if (separator > 0) {
                String suffix = token.substring(separator + 1).trim();
                if (suffix.matches("[0-9]+")) {
                    item = token.substring(0, separator).trim();
                    amount = integer(suffix, "amount");
                }
            }
            if (item.isBlank() || amount <= 0) throw new IllegalArgumentException("Invalid item reward");
            output.add(Map.of("id", item, "amount", amount));
        }
        return output;
    }

    private static void writeComposition(YamlConfiguration yaml, String minePath, MineComposition composition) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        composition.entries().forEach(entry -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("provider", entry.block().provider());
            map.put("block", entry.block().id());
            map.put("weight", entry.weight());
            if (entry.minimumPrestige() != null) map.put("minimum-prestige", entry.minimumPrestige());
            if (!entry.properties().isEmpty()) map.put("properties", entry.properties());
            if (entry.explicitAir()) map.put("allow-air", true);
            serialized.add(map);
        });
        yaml.set(minePath + ".composition", serialized);
    }

    private static CompositionInput compositionInput(String block, String raw) {
        String text = Objects.requireNonNullElse(raw, "").trim();
        if (text.isBlank()) throw new IllegalArgumentException("Composition weight is required");
        String[] tokens = text.split("\\s+");
        double weight = doubleValue(tokens[0], "weight");
        String minimumPrestige = null;
        boolean explicitAir = false;
        Map<String, String> properties = new java.util.LinkedHashMap<>();
        for (int index = 1; index < tokens.length; index++) {
            String[] pair = tokens[index].split("=", 2);
            if (pair.length != 2 || pair[0].isBlank()) throw new IllegalArgumentException("Metadata must use key=value");
            String key = pair[0].toLowerCase(Locale.ROOT);
            switch (key) {
                case "prestige", "minimum-prestige" -> minimumPrestige = pair[1].equalsIgnoreCase("none") ? null
                        : PrestigeDefinition.normalize(pair[1]);
                case "allow-air", "explicit-air" -> explicitAir = parseBoolean(pair[1]);
                default -> properties.put(pair[0], pair[1]);
            }
        }
        return new CompositionInput(block, weight, minimumPrestige, Map.copyOf(properties), explicitAir);
    }

    private static Material displayMaterial(ConfigurationSection section, String fallback) {
        Material material = Material.matchMaterial(section.getString("display-material", fallback));
        if (material == null || !material.isItem()) throw new IllegalArgumentException("Invalid display material");
        return material;
    }

    private static void renameSection(YamlConfiguration yaml, String oldPath, String newPath, String type) {
        if (oldPath.equals(newPath)) return;
        if (yaml.contains(newPath)) throw new IllegalArgumentException("Duplicate " + type + " ID");
        ConfigurationSection existing = yaml.getConfigurationSection(oldPath);
        if (existing == null) throw new IllegalArgumentException("Missing " + type);
        Map<String, Object> values = existing.getValues(true);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) yaml.set(newPath + "." + entry.getKey(), entry.getValue());
        }
        yaml.set(oldPath, null);
    }

    private static void rewriteRequirementReferences(ConfigurationSection root, String type,
                                                     String previousId, String nextId) {
        if (root == null) return;
        String previous = type + ":" + previousId;
        String replacement = type + ":" + nextId;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            List<String> requirements = section.getStringList("requirements").stream()
                    .map(value -> value.equalsIgnoreCase(previous) ? replacement : value).toList();
            if (!requirements.isEmpty()) section.set("requirements", requirements);
        }
    }

    private static void validateRankRequirements(List<RankDefinition> definitions, Set<String> prestigeIds) {
        Map<String, RankDefinition> byId = definitions.stream().collect(java.util.stream.Collectors.toMap(
                RankDefinition::id, value -> value));
        for (RankDefinition rank : definitions) {
            for (String requirement : rank.requirements()) {
                String[] parts = requirement.toLowerCase(Locale.ROOT).split(":", 2);
                if (parts.length != 2 || parts[1].isBlank()) throw new IllegalArgumentException("Invalid rank requirement: " + requirement);
                if (parts[0].equals("permission")) validatePermission(parts[1]);
                else if (parts[0].equals("rank")) {
                    RankDefinition required = byId.get(parts[1]);
                    if (required == null) throw new IllegalArgumentException("Unknown required rank: " + parts[1]);
                    if (required.order() >= rank.order()) throw new IllegalArgumentException("Rank requirement creates impossible progression: " + requirement);
                } else if (parts[0].equals("prestige")) {
                    if (!prestigeIds.contains(parts[1])) throw new IllegalArgumentException("Unknown required prestige: " + parts[1]);
                } else throw new IllegalArgumentException("Unsupported rank requirement: " + requirement);
            }
        }
    }

    private static void validatePrestigeRequirements(ConfigurationSection root, Set<String> rankIds) {
        Map<String, Integer> orders = new java.util.HashMap<>();
        int natural = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            orders.put(PrestigeDefinition.normalize(key), section == null ? natural : section.getInt("order", natural));
            natural++;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            int order = orders.get(PrestigeDefinition.normalize(key));
            for (String requirement : section.getStringList("requirements")) {
                String[] parts = requirement.toLowerCase(Locale.ROOT).split(":", 2);
                if (parts.length != 2 || parts[1].isBlank()) throw new IllegalArgumentException("Invalid prestige requirement: " + requirement);
                if (parts[0].equals("permission")) validatePermission(parts[1]);
                else if (parts[0].equals("prestige")) {
                    Integer requiredOrder = orders.get(parts[1]);
                    if (requiredOrder == null) throw new IllegalArgumentException("Unknown required prestige: " + parts[1]);
                    if (requiredOrder >= order) throw new IllegalArgumentException("Prestige requirement creates a loop: " + requirement);
                } else if (parts[0].equals("rank")) {
                    if (!rankIds.contains(parts[1])) throw new IllegalArgumentException("Unknown required rank: " + parts[1]);
                } else throw new IllegalArgumentException("Unsupported prestige requirement: " + requirement);
            }
        }
    }

    private static void validatePermission(String permission) {
        if (!permission.matches("[a-z0-9_.-]{1,128}")) throw new IllegalArgumentException("Invalid permission requirement");
    }

    private static void validateNamespacedId(String id, String type) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException(type + " is invalid: " + id);
        }
    }

    private Set<String> configurationIds(String fileName, String rootPath) {
        try {
            ConfigurationSection root = load(dataFolder.resolve(fileName)).getConfigurationSection(rootPath);
            if (root == null) return Set.of();
            return root.getKeys(false).stream().map(value -> value.toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to validate " + rootPath + " references", error);
        }
    }

    private static void deleteTree(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    private String minePath(EditRequest request) {
        String id = MineDefinition.normalizeId(requiredTarget(request));
        String path = "mines." + id;
        return path;
    }

    private static String sellPricePath(String rawTarget) {
        String target = rawTarget.trim();
        if (target.toLowerCase(Locale.ROOT).startsWith("custom:")) {
            String id = target.substring("custom:".length()).toLowerCase(Locale.ROOT);
            if (id.chars().filter(ch -> ch == ':').count() != 1) {
                throw new IllegalArgumentException("Custom sell target must be custom:namespace:id");
            }
            validateNamespacedId(id, "Custom sell target");
            return "custom-prices." + id.replace(':', '.');
        }
        String materialName = target.toUpperCase(Locale.ROOT).replace("VANILLA:", "");
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) throw new IllegalArgumentException("Unknown material: " + target);
        return "prices." + material.name();
    }

    private static boolean hasReward(ConfigurationSection rewards) {
        if (rewards == null) return false;
        if (decimal(rewards.get("money", "0"), "money").signum() > 0) return true;
        if (!rewards.getMapList("items").isEmpty()) return true;
        if (!rewards.getStringList("commands").isEmpty()) return true;
        if (!rewards.getStringList("announcements").isEmpty()) return true;
        if (rewards.getInt("xp", rewards.getInt("experience", 0)) > 0) return true;
        ConfigurationSection booster = rewards.getConfigurationSection("booster");
        return booster != null && decimal(booster.get("multiplier", "0"), "booster.multiplier").signum() > 0
                && booster.getLong("duration-seconds", 0L) > 0;
    }

    private static ConfigurationSection requireRoot(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException(path + " is missing or empty");
        }
        return section;
    }

    private static void requireSection(YamlConfiguration yaml, String path) {
        if (yaml.getConfigurationSection(path) == null) throw new IllegalArgumentException("Missing entry: " + path);
    }

    private static String requiredTarget(EditRequest request) {
        if (request.targetId() == null || request.targetId().isBlank()) throw new IllegalArgumentException("Target is required");
        return request.targetId().trim();
    }

    private static String requiredProperty(EditRequest request) {
        if (request.property() == null || request.property().isBlank()) throw new IllegalArgumentException("Property is required");
        return request.property().trim();
    }

    private static String normalizedProperty(String property) {
        return requiredProperty(new EditRequest(Editor.RANKS, Operation.SET_PROPERTY, "x", property, "",
                "", null, "")).toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(String value, String type) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid " + type + " ID");
        return value.toLowerCase(Locale.ROOT);
    }

    private static int maxOrder(ConfigurationSection root) {
        if (root == null) return -1;
        int max = -1;
        int natural = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            max = Math.max(max, section == null ? natural : section.getInt("order", natural));
            natural++;
        }
        return max;
    }

    private static BigDecimal decimalText(String value, BigDecimal fallback) {
        return (value == null || value.isBlank() ? fallback : decimal(value, "value")).stripTrailingZeros();
    }

    private static BigDecimal decimal(Object raw, String path) {
        if (raw == null) throw new IllegalArgumentException("Missing decimal: " + path);
        try {
            BigDecimal value = new BigDecimal(String.valueOf(raw));
            if (!Double.isFinite(value.doubleValue())) throw new IllegalArgumentException("Decimal must be finite: " + path);
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid decimal: " + path, ex);
        }
    }

    private static void nonnegativeFinite(Object raw, String path) {
        if (decimal(raw, path).signum() < 0) throw new IllegalArgumentException(path + " cannot be negative");
    }

    private static double doubleValue(String value, String path) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed) || parsed <= 0.0D) throw new IllegalArgumentException(path + " must be positive and finite");
        return parsed;
    }

    private static double doubleNumber(Object value, String path) {
        try {
            double parsed = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
            if (!Double.isFinite(parsed)) throw new IllegalArgumentException(path + " must be finite");
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(path + " must be numeric", ex);
        }
    }

    private static int integer(String value, String path) {
        try { return Integer.parseInt(value.trim()); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(path + " must be an integer", ex); }
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Value must be true or false");
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("[,|]"))
                .map(String::trim).filter(part -> !part.isBlank()).toList();
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record EditorSnapshot(Editor editor, String revision, int page, int pages, List<EntrySnapshot> entries) { }
    public record EntrySnapshot(String id, String summary) { }
    public record EntryDetails(Editor editor, String revision, String id, String summary, List<String> values,
                               List<String> editableProperties) { }
    private record CompositionInput(String block, double weight, String minimumPrestige,
                                    Map<String, String> properties, boolean explicitAir) { }
    public record EditRequest(Editor editor, Operation operation, String targetId, String property, String value,
                              String expectedRevision, UUID staffId, String staffName) { }
    public record EditResult(boolean success, String message, String revision, String before, String after) { }
}
