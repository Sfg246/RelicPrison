package site.mcrelicworld.relicprison.mining;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.database.PlayerProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.concurrent.ThreadLocalRandom;

/** Precompiled custom block drop rules. */
public final class CustomDropCatalog {
    private final RelicPrisonPlugin plugin;
    private final List<DropDefinition> definitions;
    private final Map<String, List<DropDefinition>> byBlockKey;

    private CustomDropCatalog(RelicPrisonPlugin plugin, List<DropDefinition> definitions) {
        this.plugin = plugin;
        this.definitions = List.copyOf(definitions);
        Map<String, List<DropDefinition>> index = new LinkedHashMap<>();
        for (DropDefinition definition : definitions) {
            for (Material material : definition.materials()) {
                index.computeIfAbsent(material.name().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                        .add(definition);
            }
            for (String customBlock : definition.customBlocks()) {
                index.computeIfAbsent(customBlock, ignored -> new ArrayList<>()).add(definition);
            }
        }
        index.replaceAll((key, values) -> values.stream().sorted(DropDefinition.ORDER).toList());
        this.byBlockKey = Map.copyOf(index);
    }

    public static CustomDropCatalog load(RelicPrisonPlugin plugin) throws Exception {
        File file = new File(plugin.getDataFolder(), "custom-drops.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("custom-drops");
        if (root != null) return new CustomDropCatalog(plugin, parseModern(plugin, root));
        ConfigurationSection legacy = yaml.getConfigurationSection("drops");
        return new CustomDropCatalog(plugin, parseLegacy(plugin, legacy));
    }

    public boolean hasRule(String blockId) {
        return byBlockKey.containsKey(normalize(blockId));
    }

    /** Legacy compatibility for older mining code and tests. */
    public List<ItemStack> drops(String blockId) {
        DropEvaluation evaluation = evaluate(new DropContext(null, null, null, null,
                Material.matchMaterial(blockId.toUpperCase(Locale.ROOT)), normalize(blockId), 0, false,
                64, 16), ThreadLocalRandom.current());
        return evaluation.items();
    }

    public DropEvaluation evaluate(DropContext context) {
        return evaluate(context, ThreadLocalRandom.current());
    }

    public DropEvaluation evaluate(DropContext context, RandomGenerator random) {
        String rawBlockKey = context.customBlockId() != null ? context.customBlockId()
                : context.material() == null ? "" : context.material().name();
        String blockKey = normalize(rawBlockKey);
        List<DropDefinition> candidates = byBlockKey.getOrDefault(blockKey, List.of());
        if (candidates.isEmpty()) return DropEvaluation.empty();
        List<ItemStack> items = new ArrayList<>();
        Map<String, Integer> commands = new LinkedHashMap<>();
        int itemRewards = 0;
        int commandRewards = 0;
        Set<String> triggeredDefinitions = new HashSet<>();
        for (DropDefinition definition : candidates) {
            if (!definition.matches(plugin, context)) continue;
            if (random.nextDouble() > definition.chance()) continue;
            triggeredDefinitions.add(definition.id());
            int definitionItemLimit = itemRewards + Math.min(context.itemLimit(), definition.perActionItemLimit());
            for (ItemReward reward : definition.items()) {
                if (itemRewards >= context.itemLimit() || itemRewards >= definitionItemLimit) break;
                int amount = reward.roll(random);
                if (amount <= 0) continue;
                if (definition.fortuneApplicable() && context.fortuneLevel() > 0) {
                    amount = Math.min(Integer.MAX_VALUE / 2, amount * Math.max(1, context.fortuneLevel() + 1));
                }
                for (ItemStack stack : createItem(reward.itemId(), amount)) {
                    if (itemRewards >= context.itemLimit() || itemRewards >= definitionItemLimit) break;
                    items.add(stack);
                    itemRewards++;
                }
            }
            int definitionCommandLimit = commandRewards + Math.min(context.commandLimit(), definition.perActionCommandLimit());
            for (CommandReward reward : definition.commands()) {
                if (commandRewards >= context.commandLimit() || commandRewards >= definitionCommandLimit) break;
                int amount = Math.max(1, reward.roll(random));
                commands.merge(reward.command(), amount, Integer::sum);
                commandRewards++;
            }
            if (definition.exclusive()) break;
        }
        return new DropEvaluation(List.copyOf(items), Map.copyOf(commands), Set.copyOf(triggeredDefinitions));
    }

    public void executeCommands(Player player, Map<String, Integer> commandAmounts, int blocks) {
        if (player == null || commandAmounts.isEmpty()) return;
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Custom drop commands must run on the server thread");
        int index = 0;
        for (String command : renderCommands(player, commandAmounts, blocks)) {
            boolean success = plugin.commandDispatch() == null
                    ? Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                    : plugin.commandDispatch().dispatchConsole("custom-drops", command);
            if (!success) throw new IllegalStateException("Custom drop command " + index + " failed");
            index++;
        }
    }

    public List<String> renderCommands(Player player, Map<String, Integer> commandAmounts, int blocks) {
        if (player == null || commandAmounts.isEmpty()) return List.of();
        List<String> rendered = new ArrayList<>();
        for (var entry : commandAmounts.entrySet()) {
            rendered.add(entry.getKey()
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%amount%", String.valueOf(entry.getValue()))
                    .replace("%blocks%", String.valueOf(blocks)));
        }
        return List.copyOf(rendered);
    }

    public List<DropDefinition> definitions() { return definitions; }
    public Map<String, List<DropRule>> rules() {
        Map<String, List<DropRule>> result = new LinkedHashMap<>();
        for (var entry : byBlockKey.entrySet()) {
            List<DropRule> rules = entry.getValue().stream()
                    .flatMap(definition -> definition.items().stream())
                    .map(item -> new DropRule(item.itemId(), item.minimum(), item.maximum(), item.chancePercent()))
                    .toList();
            result.put(entry.getKey(), rules);
        }
        return Map.copyOf(result);
    }

    private List<ItemStack> createItem(String itemId, int amount) {
        List<ItemStack> output = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            int requested = Math.min(64, remaining);
            ItemStack item;
            if (itemId.contains(":")) {
                Optional<ItemStack> custom = plugin.itemsAdder().item(itemId, requested);
                if (custom.isEmpty()) break;
                item = custom.get();
            } else {
                Material material = Material.matchMaterial(itemId.toUpperCase(Locale.ROOT));
                if (material == null || !material.isItem()) break;
                item = new ItemStack(material, Math.min(material.getMaxStackSize(), requested));
            }
            output.add(item);
            remaining -= Math.max(1, item.getAmount());
        }
        return output;
    }

    private static List<DropDefinition> parseModern(RelicPrisonPlugin plugin, ConfigurationSection root) {
        List<DropDefinition> result = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            Set<Material> materials = new HashSet<>();
            Set<String> customBlocks = new HashSet<>();
            for (String raw : section.getStringList("blocks")) {
                String value = normalize(raw);
                Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
                if (material != null) materials.add(material);
                else customBlocks.add(value);
            }
            List<ItemReward> items = new ArrayList<>();
            ConfigurationSection rewards = section.getConfigurationSection("rewards");
            if (rewards != null) {
                for (Map<?, ?> raw : rewards.getMapList("items")) items.add(parseItemReward(id, raw));
            }
            List<CommandReward> commands = new ArrayList<>();
            if (rewards != null) {
                for (Object raw : rewards.getList("commands", List.of())) commands.add(parseCommandReward(id, raw));
            }
            result.add(new DropDefinition(normalize(id), section.getBoolean("enabled", true),
                    section.getInt("priority", 0), materials, customBlocks,
                    lowerSet(section.getStringList("mines")),
                    lower(section.getString("minimum-rank")),
                    lower(section.getString("minimum-prestige")),
                    section.getStringList("permissions").stream().filter(value -> !value.isBlank()).toList(),
                    chance(section.get("chance"), 1.0D),
                    section.getBoolean("fortune-applicable", false),
                    Math.max(1, section.getInt("per-action-item-limit", 64)),
                    Math.max(0, section.getInt("per-action-command-limit", 16)),
                    section.getBoolean("exclusive", false), List.copyOf(items), List.copyOf(commands)));
        }
        return result.stream().sorted(DropDefinition.ORDER).toList();
    }

    private static List<DropDefinition> parseLegacy(RelicPrisonPlugin plugin, ConfigurationSection root) {
        if (root == null) return List.of();
        List<DropDefinition> result = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            Set<Material> materials = new HashSet<>();
            Set<String> customBlocks = new HashSet<>();
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (material == null) customBlocks.add(normalize(key));
            else materials.add(material);
            List<ItemReward> rewards = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("items")) rewards.add(parseItemReward(key, raw));
            result.add(new DropDefinition(normalize(key), true, 0, materials, customBlocks, Set.of(),
                    null, null, List.of(), 1.0D, false, 64, 16, false, List.copyOf(rewards), List.of()));
        }
        return result;
    }

    private static ItemReward parseItemReward(String id, Map<?, ?> raw) {
        Object configuredItem = raw.containsKey("id") ? raw.get("id") : raw.get("item");
        String item = string(configuredItem, "Missing item id for custom drop " + id);
        int amount = integer(raw.get("amount"), -1);
        int minimum = amount > 0 ? amount : integer(raw.get("minimum"), 1);
        int maximum = amount > 0 ? amount : integer(raw.get("maximum"), minimum);
        double chance = chance(raw.get("chance"), 1.0D);
        if (minimum < 0 || maximum < minimum) throw new IllegalArgumentException("Invalid amount range for custom drop " + id);
        return new ItemReward(normalize(item), minimum, maximum, chance);
    }

    private static CommandReward parseCommandReward(String id, Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return new CommandReward(string(map.get("command"), "Missing command for custom drop " + id),
                    integer(map.get("minimum"), integer(map.get("amount"), 1)),
                    integer(map.get("maximum"), integer(map.get("amount"), 1)),
                    chance(map.get("chance"), 1.0D));
        }
        return new CommandReward(String.valueOf(raw), 1, 1, 1.0D);
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static double chance(Object value, double fallback) {
        if (value == null) return fallback;
        double raw = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
        if (raw < 0) throw new IllegalArgumentException("Chance cannot be negative");
        if (raw > 1.0D) raw /= 100.0D;
        return Math.max(0.0D, Math.min(1.0D, raw));
    }

    private static String string(Object value, String error) {
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(error);
        return String.valueOf(value).trim();
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) if (value != null && !value.isBlank()) result.add(lower(value));
        return Set.copyOf(result);
    }

    private static String lower(String value) {
        return value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record DropContext(Player player, PlayerProfile profile, String mineId, String source,
                              Material material, String customBlockId, int fortuneLevel, boolean bulk,
                              int itemLimit, int commandLimit) { }

    public record DropEvaluation(List<ItemStack> items, Map<String, Integer> commands, Set<String> definitions) {
        public DropEvaluation {
            items = List.copyOf(items);
            commands = Map.copyOf(commands);
            definitions = Set.copyOf(definitions);
        }
        static DropEvaluation empty() { return new DropEvaluation(List.of(), Map.of(), Set.of()); }
        public boolean matched() { return !definitions.isEmpty(); }
    }

    public record DropDefinition(String id, boolean enabled, int priority, Set<Material> materials,
                                 Set<String> customBlocks, Set<String> mines, String minimumRank,
                                 String minimumPrestige, List<String> permissions, double chance,
                                 boolean fortuneApplicable, int perActionItemLimit, int perActionCommandLimit,
                                 boolean exclusive, List<ItemReward> items, List<CommandReward> commands) {
        static final Comparator<DropDefinition> ORDER = Comparator
                .comparingInt(DropDefinition::priority).reversed()
                .thenComparing(DropDefinition::id);

        public DropDefinition {
            materials = Set.copyOf(materials);
            customBlocks = Set.copyOf(customBlocks);
            mines = Set.copyOf(mines);
            permissions = List.copyOf(permissions);
            items = List.copyOf(items);
            commands = List.copyOf(commands);
        }

        boolean matches(RelicPrisonPlugin plugin, DropContext context) {
            if (!enabled) return false;
            if (!mines.isEmpty() && (context.mineId() == null
                    || !mines.contains(context.mineId().toLowerCase(Locale.ROOT)))) return false;
            if (minimumRank != null && context.profile() != null
                    && plugin.rankService().indexOf(context.profile().currentRank())
                    < plugin.rankService().indexOf(minimumRank)) return false;
            if (minimumPrestige != null) {
                int playerPrestige = context.profile() == null ? -1
                        : plugin.prestigeService().indexOf(context.profile().currentPrestige());
                if (playerPrestige < plugin.prestigeService().indexOf(minimumPrestige)) return false;
            }
            if (context.player() != null) {
                for (String permission : permissions) if (!context.player().hasPermission(permission)) return false;
            } else if (!permissions.isEmpty()) return false;
            return true;
        }
    }

    public record ItemReward(String itemId, int minimum, int maximum, double chance) {
        int roll(RandomGenerator random) {
            if (random.nextDouble() > chance) return 0;
            return minimum == maximum ? minimum : random.nextInt(minimum, maximum + 1);
        }
        double chancePercent() { return chance * 100.0D; }
    }

    public record CommandReward(String command, int minimum, int maximum, double chance) {
        int roll(RandomGenerator random) {
            if (random.nextDouble() > chance) return 0;
            return minimum == maximum ? minimum : random.nextInt(minimum, maximum + 1);
        }
    }

    public record DropRule(String itemId, int minimum, int maximum, double chance) { }
}
