package site.mcrelicworld.relicprison.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandPresentationTest {
    @Test
    void rendersStructuredPaginatedHelp() {
        MessageService.HelpSection section = new MessageService.HelpSection("Mining", List.of(
                new MessageService.HelpEntry("/mine", "Open the mine menu"),
                new MessageService.HelpEntry("/rankup", "Advance to the next rank")));

        List<String> lines = MessageService.helpLines("RelicPrison", List.of(section), 1, 3, "prison");

        assertEquals("&8&m----------------------------------------", lines.getFirst());
        assertEquals("&b&lRelicPrison &7- &fCommands", lines.get(1));
        assertTrue(lines.contains("&bMining"));
        assertTrue(lines.contains(" &e/mine &8- &7Open the mine menu"));
        assertTrue(lines.contains("&7Page &f1&8/&f3"));
        assertTrue(lines.contains("&eUse /prison help 2 for the next page."));
        assertEquals("&8&m----------------------------------------", lines.getLast());
    }

    @Test
    void packagedMessagesUseStandardPrefixesAndValueColors() throws Exception {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());

        assertEquals("&8[&b&lRelicPrison&8] &r", yaml.getString("prefix"));
        assertEquals("&8[&6&lGang&8] &r", yaml.getString("gang-prefix"));
        assertTrue(yaml.getString("sell-success", "").contains("&6{amount}"));
        assertTrue(yaml.getString("prestige-success", "").contains("&d{prestige}"));
    }

    @Test
    void commandHandlersUseCentralizedPresentation() throws Exception {
        try (var files = Files.list(Path.of("src/main/java/site/mcrelicworld/relicprison/command"))) {
            List<Path> handlers = files.filter(path -> path.toString().endsWith("Command.java")).toList();
            assertFalse(handlers.isEmpty());
            for (Path handler : handlers) {
                assertFalse(Files.readString(handler).contains(".sendMessage("),
                        handler.getFileName() + " bypasses MessageService");
            }
        }
    }
}
