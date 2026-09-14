package site.mcrelicworld.relicprison.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JarContentVerificationIT {
    @Test
    void productionJarContainsOnlyProductionRelicPrisonClassesAndResources() throws Exception {
        Path jar = productionJar();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            List<String> entries = jarFile.stream().map(entry -> entry.getName()).toList();
            assertFalse(entries.stream().anyMatch(name -> name.endsWith("Test.class") || name.contains("/test/")),
                    "Production jar must not package tests");
            assertFalse(entries.stream().anyMatch(name -> name.startsWith("org/bukkit/")
                            || name.startsWith("io/papermc/")
                            || name.startsWith("net/luckperms/")
                            || name.startsWith("net/milkbowl/")
                            || name.startsWith("com/sk89q/")
                            || name.startsWith("me/clip/")
                            || name.startsWith("dev/lone/")
                            || name.startsWith("net/advancedplugins/")
                            || name.startsWith("org/junit/")),
                    "Production jar must not package dependency or development stub classes");
            assertTrue(entries.contains("plugin.yml"), "plugin.yml is required");
            assertTrue(entries.contains("integrations.yml"), "integrations.yml is required");
            String pluginYaml = new String(jarFile.getInputStream(jarFile.getJarEntry("plugin.yml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(pluginYaml.contains("version: '1.0.0'"),
                    "plugin.yml must contain the official 1.0.0 version");
        }
    }

    private static Path productionJar() throws Exception {
        try (var files = Files.list(Path.of("target"))) {
            List<Path> jars = files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("RelicPrison-") && name.endsWith(".jar") && !name.endsWith("-sources.jar");
            }).toList();
            if (jars.size() != 1) throw new IllegalStateException("Expected one production jar, found " + jars);
            return jars.getFirst();
        }
    }
}
