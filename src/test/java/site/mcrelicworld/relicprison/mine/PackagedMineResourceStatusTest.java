package site.mcrelicworld.relicprison.mine;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedMineResourceStatusTest {
    @Test
    void packagedResourceReportsMissingApprovedThirtyThreeMineAsset() {
        PackagedMineResourceStatus status = PackagedMineResourceStatus.inspect(
                getClass().getClassLoader().getResourceAsStream("mines.yml"));

        assertTrue(status.present());
        assertEquals(33, status.mineCount());
        assertTrue(status.approvedThirtyThreeMineAsset(), String.join("; ", status.errors()));
        assertFalse(status.missingApprovedAsset());
        assertFalse(String.join(" ", status.errors()).contains("33-mine"));
    }

    @Test
    void exactThirtyThreeMineAssetWouldBeRecognizedWhenSupplied() {
        StringBuilder yaml = new StringBuilder("file-version: 1\nmines:\n");
        for (int index = 1; index <= 33; index++) {
            yaml.append("  mine").append(index).append(":\n")
                    .append("    display-name: Mine ").append(index).append("\n")
                    .append("    world:\n")
                    .append("      uuid: 00000000-0000-0000-0000-0000000000")
                    .append(index < 10 ? "0" : "").append(index).append("\n")
                    .append("      name: world\n")
                    .append("    minimum: {x: 0, y: 0, z: 0}\n")
                    .append("    maximum: {x: 10, y: 10, z: 10}\n")
                    .append("    composition:\n")
                    .append("      - provider: vanilla\n")
                    .append("        block: STONE\n")
                    .append("        weight: 1.0\n")
                    .append("    reset:\n")
                    .append("      interval-seconds: 900\n");
        }

        PackagedMineResourceStatus status = PackagedMineResourceStatus.inspect(
                new ByteArrayInputStream(yaml.toString().getBytes(StandardCharsets.UTF_8)));

        assertEquals(33, status.mineCount());
        assertTrue(status.approvedThirtyThreeMineAsset(), String.join("; ", status.errors()));
    }

    @Test
    void invalidNonEmptyMineAssetIsRejectedInsteadOfPretendingFreshInstallIsComplete() {
        String yaml = """
                file-version: 1
                mines:
                  a:
                    display-name: A
                """;
        PackagedMineResourceStatus status = PackagedMineResourceStatus.inspect(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, status.mineCount());
        assertFalse(status.approvedThirtyThreeMineAsset());
        assertTrue(status.errors().stream().anyMatch(error -> error.contains("world.name")));
    }
}

