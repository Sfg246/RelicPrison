package site.mcrelicworld.relicprison.message;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import site.mcrelicworld.relicprison.util.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageService {
    private static final String SEPARATOR = "&8&m----------------------------------------";
    private final JavaPlugin plugin;
    private final File file;
    private volatile Map<String, String> messages = Map.of();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
    }

    public Map<String, String> preview() throws Exception {
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        Map<String, String> loaded = new ConcurrentHashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) loaded.put(key, yaml.getString(key, ""));
        }
        if (!loaded.containsKey("prefix")) throw new IllegalArgumentException("messages.yml is missing prefix");
        return Map.copyOf(loaded);
    }

    public void apply(Map<String, String> loaded) { messages = Map.copyOf(loaded); }
    public Map<String, String> snapshot() { return Map.copyOf(messages); }

    public void load() throws Exception { apply(preview()); }

    public String raw(String key) { return messages.getOrDefault(key, key); }

    public String format(String key, Map<String, ?> placeholders) {
        String value = formatPlain(key, placeholders);
        return value.isEmpty() ? "" : ColorUtil.color(raw("prefix")) + value;
    }

    public String formatPlain(String key, Map<String, ?> placeholders) {
        String value = raw(key);
        if (value == null || value.isBlank()) return "";
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return ColorUtil.color(value);
    }

    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }
    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        String message = format(key, placeholders);
        if (!message.isEmpty()) sender.sendMessage(message);
    }

    public void styled(CommandSender sender, String message) {
        sender.sendMessage(ColorUtil.color(message));
    }

    public void success(CommandSender sender, String message) { prefixed(sender, "prefix", "&a", message); }
    public void error(CommandSender sender, String message) { prefixed(sender, "prefix", "&c", message); }
    public void warning(CommandSender sender, String message) { prefixed(sender, "prefix", "&e", message); }
    public void info(CommandSender sender, String message) { prefixed(sender, "prefix", "&7", message); }
    public void gangSuccess(CommandSender sender, String message) { prefixed(sender, "gang-prefix", "&a", message); }
    public void gangError(CommandSender sender, String message) { prefixed(sender, "gang-prefix", "&c", message); }
    public void gangWarning(CommandSender sender, String message) { prefixed(sender, "gang-prefix", "&e", message); }
    public void gangInfo(CommandSender sender, String message) { prefixed(sender, "gang-prefix", "&7", message); }

    public void gangResult(CommandSender sender, boolean success, String message) {
        String[] lines = sentence(message).split("\\R");
        if (success) gangSuccess(sender, lines[0]);
        else gangError(sender, lines[0]);
        for (int index = 1; index < lines.length; index++) styled(sender, lines[index]);
    }

    public void usage(CommandSender sender, String syntax) {
        error(sender, "Invalid usage.");
        styled(sender, "&7Usage: &e" + stripUsage(syntax));
    }

    public void gangUsage(CommandSender sender, String syntax) {
        gangError(sender, "Invalid usage.");
        styled(sender, "&7Usage: &e" + stripUsage(syntax));
    }

    public void sectionHeader(CommandSender sender, String title, String color) {
        styled(sender, SEPARATOR);
        styled(sender, color + "&l" + title);
        styled(sender, SEPARATOR);
    }

    public void sectionFooter(CommandSender sender) { styled(sender, SEPARATOR); }

    public void field(CommandSender sender, String label, String valueColor, Object value) {
        styled(sender, "&7" + label + ": " + valueColor + String.valueOf(value));
    }

    public void entry(CommandSender sender, String primary, String description) {
        styled(sender, " &e" + primary + " &8- &7" + description);
    }

    public void help(CommandSender sender, String title, List<HelpSection> sections, int page, int pages,
                     String command) {
        for (String line : helpLines(title, sections, page, pages, command)) styled(sender, line);
    }

    static List<String> helpLines(String title, List<HelpSection> sections, int page, int pages, String command) {
        List<String> lines = new ArrayList<>();
        lines.add(SEPARATOR);
        lines.add("&b&l" + title + " &7- &fCommands");
        lines.add(SEPARATOR);
        lines.add("");
        for (HelpSection section : sections) {
            lines.add("&b" + section.title());
            for (HelpEntry entry : section.entries()) {
                lines.add(" &e" + entry.syntax() + " &8- &7" + entry.description());
            }
            lines.add("");
        }
        lines.add("&7Page &f" + page + "&8/&f" + pages);
        if (page < pages) lines.add("&eUse /" + command + " help " + (page + 1) + " for the next page.");
        else if (pages > 1) lines.add("&eUse /" + command + " help 1 to return to the first page.");
        lines.add(SEPARATOR);
        return List.copyOf(lines);
    }

    private void prefixed(CommandSender sender, String prefixKey, String color, String message) {
        String prefix = raw(prefixKey);
        if (prefix.equals(prefixKey)) prefix = raw("prefix");
        sender.sendMessage(ColorUtil.color(prefix + color + message));
    }

    private static String stripUsage(String syntax) {
        String value = syntax == null ? "" : syntax.trim();
        return value.regionMatches(true, 0, "Usage:", 0, 6) ? value.substring(6).trim() : value;
    }

    private static String sentence(String message) {
        if (message == null || message.isBlank()) return "The command could not be completed.";
        String value = message.trim();
        char last = value.charAt(value.length() - 1);
        return last == '.' || last == '!' || last == '?' ? value : value + ".";
    }

    public record HelpEntry(String syntax, String description) { }
    public record HelpSection(String title, List<HelpEntry> entries) {
        public HelpSection { entries = List.copyOf(entries); }
    }
}
