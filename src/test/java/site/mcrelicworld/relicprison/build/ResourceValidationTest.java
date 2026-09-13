package site.mcrelicworld.relicprison.build;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ResourceValidationTest {
    @Test
    void yamlAndSqlResourcesAreValid() throws Exception {
        List<String> failures = new ArrayList<>();
        validateTree(Path.of("src/main/resources"), failures);
        validateTree(Path.of(".github/workflows"), failures);
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    @Test
    void packagedConfigSeparatesPlayerStatisticsFromMineAnalytics() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(Path.of("src/main/resources/config.yml").toFile());

        assertTrue(yaml.getBoolean("features.player-mining-statistics"),
                "player mining statistics must be enabled by default");
        assertFalse(yaml.getBoolean("features.mine-analytics"),
                "mine analytics must remain independently disabled by default");
    }

    private static void validateTree(Path root, List<String> failures) throws Exception {
        if (!Files.exists(root)) return;
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) validateFile(file, failures);
        }
    }

    private static void validateFile(Path file, List<String> failures) throws Exception {
        String name = file.toString().replace('\\', '/');
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file.toFile());
            } catch (Exception ex) {
                failures.add(name + ": " + ex.getMessage());
            }
        }
        if (name.endsWith(".sql") && Files.readString(file).isBlank()) {
            failures.add(name + ": SQL resource is empty");
        }
    }
}
