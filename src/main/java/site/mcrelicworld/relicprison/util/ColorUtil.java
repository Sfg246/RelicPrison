package site.mcrelicworld.relicprison.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class ColorUtil {
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ColorUtil() {}

    public static String color(String input) {
        return SECTION.serialize(component(input));
    }

    public static Component component(String input) {
        return AMPERSAND.deserialize(input == null ? "" : input);
    }

    public static Component legacyComponent(String input) {
        return SECTION.deserialize(input == null ? "" : input);
    }

    public static String plain(String input) {
        return PLAIN.serialize(component(input));
    }

    public static String plain(Component input) {
        return input == null ? "" : PLAIN.serialize(input);
    }

    public static void showTitle(Player player, String title, String subtitle,
                                 int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.showTitle(Title.title(legacyComponent(title), legacyComponent(subtitle), Title.Times.times(
                ticks(fadeInTicks), ticks(stayTicks), ticks(fadeOutTicks))));
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(Math.max(0, ticks) * 50L);
    }
}
