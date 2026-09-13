package site.mcrelicworld.relicprison.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {
    @Test void parsesCompoundDurationsAndFormatsThem() {
        assertEquals(Duration.ofSeconds(93784), DurationParser.parse("1d2h3m4s"));
        assertEquals("1d2h3m4s", DurationParser.format(93_784_000L));
    }

    @Test void rejectsZeroAndMalformedDurations() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("0m"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("2hours"));
    }
}
