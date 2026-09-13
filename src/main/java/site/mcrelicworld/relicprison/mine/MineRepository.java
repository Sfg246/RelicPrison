package site.mcrelicworld.relicprison.mine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import site.mcrelicworld.relicprison.api.model.BlockPosition;
import site.mcrelicworld.relicprison.mine.composition.BlockTypeRef;
import site.mcrelicworld.relicprison.mine.composition.CompositionEntry;
import site.mcrelicworld.relicprison.mine.composition.MineComposition;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MineRepository {
    private final JavaPlugin plugin;
    private final File file;

    public MineRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mines.yml");
    }

    public Map<String, MineDefinition> load() throws Exception {
        if (!file.exists()) plugin.saveResource("mines.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("mines");
        Map<String, MineDefinition> result = new LinkedHashMap<>();
        if (root == null) return result;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            MineDefinition mine = readDefinition(key, section);
            if (result.put(mine.id(), mine) != null) throw new IllegalArgumentException("Duplicate mine ID: " + mine.id());
        }
        return result;
    }

    public static MineDefinition readDefinition(String key, ConfigurationSection section) {
        String id = MineDefinition.normalizeId(key);
        String displayName = section.getString("display-name", key);
        UUID worldId = UUID.fromString(required(section.getString("world.uuid"), id + ".world.uuid"));
        String worldName = required(section.getString("world.name"), id + ".world.name");
        BlockPosition min = new BlockPosition(section.getInt("minimum.x"), section.getInt("minimum.y"), section.getInt("minimum.z"));
        BlockPosition max = new BlockPosition(section.getInt("maximum.x"), section.getInt("maximum.y"), section.getInt("maximum.z"));
        MineSpawn spawn = null;
        if (section.getBoolean("spawn.present", false)) {
            spawn = new MineSpawn(
                    section.getDouble("spawn.x"), section.getDouble("spawn.y"), section.getDouble("spawn.z"),
                    (float) section.getDouble("spawn.yaw"), (float) section.getDouble("spawn.pitch")
            );
        }
        List<CompositionEntry> entries = new ArrayList<>();
        for (Map<?, ?> rawEntry : section.getMapList("composition")) {
            Object blockValue = rawEntry.get("block");
            Object weightValue = rawEntry.get("weight");
            if (!(blockValue instanceof String blockId) || !(weightValue instanceof Number weight)) {
                throw new IllegalArgumentException(id + ".composition entries require block and numeric weight");
            }
            String provider = rawEntry.get("provider") instanceof String rawProvider
                    ? rawProvider.trim().toLowerCase(java.util.Locale.ROOT) : "";
            String qualified = provider.isBlank() ? blockId : provider + ":" + blockId;
            String minimumPrestige = rawEntry.get("minimum-prestige") instanceof String rawPrestige ? rawPrestige : null;
            boolean explicitAir = Boolean.TRUE.equals(rawEntry.get("allow-air"));
            Map<String, String> properties = parseProperties(rawEntry.get("properties"));
            entries.add(new CompositionEntry(BlockTypeRef.parse(qualified), weight.doubleValue(), minimumPrestige,
                    properties, explicitAir));
        }
        if (entries.isEmpty()) {
            ConfigurationSection compositionSection = section.getConfigurationSection("composition");
            if (compositionSection != null) {
                for (String blockId : compositionSection.getKeys(false)) {
                    entries.add(new CompositionEntry(BlockTypeRef.parse(blockId), compositionSection.getDouble(blockId)));
                }
            }
        }
        MineComposition composition = entries.isEmpty() ? MineComposition.defaultStone() : new MineComposition(entries);
        MineResetConfig resetConfig = parseReset(section);
        Map<String, String> metadata = parseMetadata(section.getConfigurationSection("metadata"));
        return new MineDefinition(
                id, displayName, worldId, worldName, new Cuboid(min, max), spawn,
                section.getBoolean("enabled", true), section.getInt("sort-order", 0),
                blankToNull(section.getString("required-rank")), blankToNull(section.getString("required-prestige")),
                blankToNull(section.getString("access-permission")), metadata, composition, resetConfig
        );
    }

    public synchronized void save(Collection<MineDefinition> mines) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("file-version", 1);
        List<MineDefinition> sorted = mines.stream()
                .sorted(Comparator.comparingInt(MineDefinition::sortOrder).thenComparing(MineDefinition::id))
                .toList();
        for (MineDefinition mine : sorted) writeDefinition(yaml, "mines." + mine.id(), mine);
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        yaml.save(temporary);
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void writeDefinition(YamlConfiguration yaml, String path, MineDefinition mine) {
        yaml.set(path + ".display-name", mine.displayName());
        yaml.set(path + ".world.uuid", mine.worldId().toString());
        yaml.set(path + ".world.name", mine.worldName());
        yaml.set(path + ".minimum.x", mine.bounds().minimum().x());
        yaml.set(path + ".minimum.y", mine.bounds().minimum().y());
        yaml.set(path + ".minimum.z", mine.bounds().minimum().z());
        yaml.set(path + ".maximum.x", mine.bounds().maximum().x());
        yaml.set(path + ".maximum.y", mine.bounds().maximum().y());
        yaml.set(path + ".maximum.z", mine.bounds().maximum().z());
        yaml.set(path + ".enabled", mine.enabled());
        yaml.set(path + ".sort-order", mine.sortOrder());
        yaml.set(path + ".required-rank", mine.requiredRank());
        yaml.set(path + ".required-prestige", mine.requiredPrestige());
        yaml.set(path + ".access-permission", mine.accessPermission());
        yaml.set(path + ".metadata", mine.metadata().isEmpty() ? null : mine.metadata());
        yaml.set(path + ".spawn.present", mine.spawn() != null);
        if (mine.spawn() != null) {
            yaml.set(path + ".spawn.x", mine.spawn().x());
            yaml.set(path + ".spawn.y", mine.spawn().y());
            yaml.set(path + ".spawn.z", mine.spawn().z());
            yaml.set(path + ".spawn.yaw", mine.spawn().yaw());
            yaml.set(path + ".spawn.pitch", mine.spawn().pitch());
        }
        List<Map<String, Object>> serializedComposition = new ArrayList<>();
        for (CompositionEntry entry : mine.composition().entries()) {
            Map<String, Object> serializedEntry = new LinkedHashMap<>();
            serializedEntry.put("provider", entry.block().provider());
            serializedEntry.put("block", entry.block().kind() == BlockTypeRef.Kind.VANILLA
                    ? entry.block().material().name() : entry.block().id());
            serializedEntry.put("weight", entry.weight());
            if (entry.minimumPrestige() != null) serializedEntry.put("minimum-prestige", entry.minimumPrestige());
            if (!entry.properties().isEmpty()) serializedEntry.put("properties", entry.properties());
            if (entry.explicitAir()) serializedEntry.put("allow-air", true);
            serializedComposition.add(serializedEntry);
        }
        yaml.set(path + ".composition", serializedComposition);
        MineResetConfig reset = mine.resetConfig();
        yaml.set(path + ".reset.timed-enabled", reset.timedEnabled());
        yaml.set(path + ".reset.interval-seconds", reset.intervalSeconds());
        yaml.set(path + ".reset.percentage-enabled", reset.percentageEnabled());
        yaml.set(path + ".reset.mined-percentage", reset.minedPercentage());
        yaml.set(path + ".reset.warning-seconds", reset.warningSeconds());
        yaml.set(path + ".reset.countdown-seconds", reset.countdownSeconds());
        yaml.set(path + ".reset.notification-scope", reset.notificationScope().name());
        yaml.set(path + ".reset.notification-radius", reset.notificationRadius());
        yaml.set(path + ".reset.evacuate-players", reset.evacuatePlayers());
        yaml.set(path + ".reset.order", reset.order().name());
        yaml.set(path + ".reset.commands.before", reset.beforeCommands());
        yaml.set(path + ".reset.commands.start", reset.startCommands());
        yaml.set(path + ".reset.commands.complete", reset.completeCommands());
        yaml.set(path + ".reset.commands.failed", reset.failedCommands());
        yaml.set(path + ".reset.enabled", reset.enabled());
        yaml.set(path + ".reset.retry-count", reset.retryCount());
        yaml.set(path + ".reset.retry-delay-seconds", reset.retryDelaySeconds());
        yaml.set(path + ".reset.recount-behavior", reset.recountBehavior().name());
        yaml.set(path + ".reset.teleport-destination", reset.teleportDestination().name());
        yaml.set(path + ".reset.require-outside-destination", reset.requireOutsideDestination());
    }

    private static MineResetConfig parseReset(ConfigurationSection section) {
        ConfigurationSection reset = section.getConfigurationSection("reset");
        if (reset == null) return MineResetConfig.defaults(900, 80.0, List.of(30, 10, 5, 3, 2, 1));
        MineResetConfig.NotificationScope scope;
        MineResetConfig.ResetOrder order;
        MineResetConfig.RecountBehavior recountBehavior;
        MineResetConfig.TeleportDestination teleportDestination;
        try {
            scope = MineResetConfig.NotificationScope.valueOf(
                    reset.getString("notification-scope", "MINE").toUpperCase(java.util.Locale.ROOT));
            order = MineResetConfig.ResetOrder.valueOf(
                    reset.getString("order", "CHUNK_GROUPED_BOTTOM_UP").toUpperCase(java.util.Locale.ROOT));
            recountBehavior = MineResetConfig.RecountBehavior.valueOf(
                    reset.getString("recount-behavior", "AFTER_FAILURE").replace('-', '_').toUpperCase(java.util.Locale.ROOT));
            teleportDestination = MineResetConfig.TeleportDestination.valueOf(
                    reset.getString("teleport-destination", "MINE_SPAWN").replace('-', '_').toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid reset notification scope or order", ex);
        }
        List<Integer> warnings = reset.getIntegerList("warning-seconds");
        if (warnings.isEmpty()) warnings = List.of(30, 10, 5, 3, 2, 1);
        return new MineResetConfig(
                reset.getBoolean("timed-enabled", true),
                Math.max(1, reset.getInt("interval-seconds", 900)),
                reset.getBoolean("percentage-enabled", true),
                reset.getDouble("mined-percentage", 80.0),
                warnings.stream().filter(value -> value != null && value >= 0).distinct()
                        .sorted(java.util.Comparator.reverseOrder()).toList(),
                reset.getInt("countdown-seconds", warnings.stream().mapToInt(Integer::intValue).max().orElse(0)),
                scope,
                Math.max(0, reset.getInt("notification-radius", 64)),
                reset.getBoolean("evacuate-players", true),
                order,
                reset.getStringList("commands.before"),
                reset.getStringList("commands.start"),
                reset.getStringList("commands.complete"),
                reset.getStringList("commands.failed"),
                reset.getBoolean("enabled", true),
                reset.getInt("retry-count", 3),
                reset.getInt("retry-delay-seconds", reset.getInt("retry-delay", 60)),
                recountBehavior,
                teleportDestination,
                reset.getBoolean("require-outside-destination", true)
        );
    }

    private static String required(String value, String path) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " is required");
        return value;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static Map<String, String> parseMetadata(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value != null) values.put(key, String.valueOf(value));
        }
        return values;
    }

    private static Map<String, String> parseProperties(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                values.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return values;
    }
}
