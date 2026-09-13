package site.mcrelicworld.relicprison.mine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class StructureOperationRepository {
    private final File file;

    public StructureOperationRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "structure-operations.yml");
    }

    public synchronized Map<UUID, StructureOperationRecord> load() throws Exception {
        Map<UUID, StructureOperationRecord> result = new LinkedHashMap<>();
        if (!file.exists()) return result;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("operations");
        if (root == null) return result;
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            UUID id = UUID.fromString(rawId);
            ConfigurationSection source = requiredSection(section, "source");
            ConfigurationSection target = requiredSection(section, "target");
            StructureOperationRecord record = new StructureOperationRecord(
                    id,
                    StructureOperationType.valueOf(section.getString("type", "COPY")),
                    StructureOperationStage.valueOf(section.getString("stage", "FAILED")),
                    UUID.fromString(required(section.getString("staff-uuid"), rawId + ".staff-uuid")),
                    MineRepository.readDefinition(section.getString("source.id", "source"), source),
                    MineRepository.readDefinition(section.getString("target.id", "target"), target),
                    section.getLong("created-at"),
                    section.getLong("updated-at"),
                    section.getLong("copied-blocks"),
                    section.getLong("cleared-blocks"),
                    section.getString("provider", "PENDING"),
                    section.getString("failure")
            );
            result.put(id, record);
        }
        return result;
    }

    public synchronized void save(Collection<StructureOperationRecord> records) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("file-version", 1);
        for (StructureOperationRecord record : records) {
            String path = "operations." + record.id();
            yaml.set(path + ".type", record.type().name());
            yaml.set(path + ".stage", record.stage().name());
            yaml.set(path + ".staff-uuid", record.staffId().toString());
            yaml.set(path + ".created-at", record.createdAt());
            yaml.set(path + ".updated-at", record.updatedAt());
            yaml.set(path + ".copied-blocks", record.copiedBlocks());
            yaml.set(path + ".cleared-blocks", record.clearedBlocks());
            yaml.set(path + ".provider", record.provider());
            yaml.set(path + ".failure", record.failure());
            yaml.set(path + ".source.id", record.source().id());
            yaml.set(path + ".target.id", record.target().id());
            MineRepository.writeDefinition(yaml, path + ".source", record.source());
            MineRepository.writeDefinition(yaml, path + ".target", record.target());
        }
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        yaml.save(temporary);
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ConfigurationSection requiredSection(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException("Missing structure operation section " + path);
        return section;
    }

    private static String required(String value, String path) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " is required");
        return value;
    }
}
