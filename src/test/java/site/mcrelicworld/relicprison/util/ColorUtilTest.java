package site.mcrelicworld.relicprison.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ColorUtilTest {
    @Test
    void parsesAmpersandFormattingIntoComponents() {
        assertEquals("RelicPrison", ColorUtil.plain(ColorUtil.component("&6RelicPrison")));
    }

    @Test
    void parsesStoredLegacyFormattingIntoComponents() {
        assertEquals("RelicPrison", ColorUtil.plain(ColorUtil.legacyComponent("\u00A76RelicPrison")));
    }
}
