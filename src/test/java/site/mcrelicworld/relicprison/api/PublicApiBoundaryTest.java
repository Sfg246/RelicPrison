package site.mcrelicworld.relicprison.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PublicApiBoundaryTest {
    @Test
    void apiTestPluginImportsOnlyPublicApiAndBukkit() throws Exception {
        Path root = Path.of("src/api-test-plugin/java");
        try (var files = Files.walk(root)) {
            List<String> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> uncheckedLines(path).stream())
                    .filter(line -> line.startsWith("import site.mcrelicworld.relicprison.")
                            && !line.startsWith("import site.mcrelicworld.relicprison.api."))
                    .toList();
            assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
        }
    }

    @Test
    void apiPackageDoesNotExposeImplementationClasses() throws Exception {
        Path root = Path.of("src/main/java/site/mcrelicworld/relicprison/api");
        try (var files = Files.walk(root)) {
            List<String> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("Impl.java") || name.endsWith("ServiceImpl.java"))
                    .toList();
            assertEquals(List.of(), violations);
        }
    }

    private static List<String> uncheckedLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
