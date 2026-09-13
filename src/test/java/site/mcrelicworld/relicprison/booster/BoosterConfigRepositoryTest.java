package site.mcrelicworld.relicprison.booster;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BoosterConfigRepositoryTest {
    @Test
    void parsesYamlNumericTypesAndScientificNotation() {
        assertEquals(new BigDecimal("1.1"),
                BoosterConfigRepository.decimalValue(1.10D, "double"));
        assertEquals(new BigDecimal("25"),
                BoosterConfigRepository.decimalValue(25, "integer"));
        assertEquals(new BigDecimal("1E+2"),
                BoosterConfigRepository.decimalValue("1e2", "scientific"));
    }

    @Test
    void reconstructsDottedPermissionNamesFromNestedSections() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("relicprison.multiplier.vip", 1.10D);
        root.set("relicprison.multiplier.mvp", "1.20");

        Map<String, BigDecimal> parsed = BoosterConfigRepository.permissionMultipliers(root);

        assertEquals(new BigDecimal("1.1"), parsed.get("relicprison.multiplier.vip"));
        assertEquals(new BigDecimal("1.20"), parsed.get("relicprison.multiplier.mvp"));
    }

    @Test
    void rejectsConfigurationSectionsAsDecimalValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("nested");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BoosterConfigRepository.decimalValue(yaml.getConfigurationSection("nested"),
                        "permission-multipliers.relicprison"));
        assertEquals("Expected a decimal at permission-multipliers.relicprison but found MemorySection",
                error.getMessage());
    }
}
