package site.mcrelicworld.relicprison.gui;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuiVisuals {
    public enum State {
        DEFAULT("&f", false), CLICKABLE("&e", false), LOCKED("&c", false), CURRENT("&b", true),
        ENABLED("&a", true), DISABLED("&c", false), DANGEROUS("&c", false), ACTIVE("&d", true),
        COMPLETED("&a", true), SELECTED("&b", true), STATUS("&b", false), UNAVAILABLE("&8", false);

        private final String color;
        private final boolean glow;

        State(String color, boolean glow) {
            this.color = color;
            this.glow = glow;
        }

        public String color() { return color; }
        public boolean glow() { return glow; }
    }

    private static final List<Material> PROGRESSION = List.of(Material.STONE, Material.COAL_ORE,
            Material.COPPER_ORE, Material.IRON_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
            Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.AMETHYST_BLOCK,
            Material.ANCIENT_DEBRIS, Material.NETHERITE_BLOCK);

    private GuiVisuals() { }

    public static Material mineMaterial(String id, String displayName, int index, boolean available) {
        if (!available) return Material.RED_STAINED_GLASS_PANE;
        String key = (id + ' ' + displayName).toLowerCase(Locale.ROOT);
        if (key.contains("coal")) return Material.COAL_ORE;
        if (key.contains("copper")) return Material.COPPER_ORE;
        if (key.contains("iron")) return Material.IRON_ORE;
        if (key.contains("lapis")) return Material.LAPIS_ORE;
        if (key.contains("redstone")) return Material.REDSTONE_ORE;
        if (key.contains("gold")) return Material.GOLD_ORE;
        if (key.contains("diamond")) return Material.DIAMOND_ORE;
        if (key.contains("emerald")) return Material.EMERALD_ORE;
        if (key.contains("immortal")) return Material.NETHERITE_BLOCK;
        return PROGRESSION.get(Math.min(PROGRESSION.size() - 1, Math.max(0, index / 3)));
    }

    public static Material rankMaterial(int index, int total, boolean locked) {
        if (locked) return Material.RED_STAINED_GLASS_PANE;
        int bucket = total <= 1 ? 0 : (int) ((long) index * (PROGRESSION.size() - 1) / (total - 1));
        return PROGRESSION.get(bucket);
    }

    public static Material prestigeMaterial(String id, int index, boolean locked) {
        if (locked) return Material.RED_STAINED_GLASS_PANE;
        return mineMaterial(id, id, Math.max(0, index * 3), true);
    }

    public static Material boosterMaterial(String type) {
        String key = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (key.contains("gang")) return Material.BLAZE_POWDER;
        if (key.contains("mining") || key.contains("xp")) return Material.EXPERIENCE_BOTTLE;
        if (key.contains("sell") || key.contains("money")) return Material.GOLD_INGOT;
        return Material.BEACON;
    }

    public static Material eventMaterial(String id) {
        String key = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (key.contains("money") || key.contains("sell")) return Material.GOLD_INGOT;
        if (key.contains("mine") || key.contains("block")) return Material.DIAMOND_PICKAXE;
        if (key.contains("rank")) return Material.EXPERIENCE_BOTTLE;
        if (key.contains("prestige")) return Material.NETHER_STAR;
        if (key.contains("reward")) return Material.CHEST;
        return Material.REDSTONE;
    }

    public static Material missionMaterial(String state) {
        return switch (state.toUpperCase(Locale.ROOT)) {
            case "COMPLETED" -> Material.LIME_WOOL;
            case "CLAIMED" -> Material.CHEST_MINECART;
            case "CANCELLED" -> Material.RED_STAINED_GLASS_PANE;
            default -> Material.WRITABLE_BOOK;
        };
    }

    public static State upgradeState(boolean maximum, boolean prerequisitesMet, boolean affordable) {
        if (maximum) return State.CURRENT;
        if (!prerequisitesMet) return State.LOCKED;
        return affordable ? State.CLICKABLE : State.DISABLED;
    }

    public static Material propertyMaterial(String property) {
        String key = property == null ? "" : property.toLowerCase(Locale.ROOT);
        if (key.contains("material") || key.contains("block")) return Material.GRASS_BLOCK;
        if (key.contains("price") || key.contains("cost") || key.contains("money")) return Material.GOLD_INGOT;
        if (key.contains("time") || key.contains("duration") || key.contains("interval")) return Material.CLOCK;
        if (key.contains("location") || key.contains("world") || key.contains("spawn")) return Material.COMPASS;
        if (key.contains("enabled") || key.contains("toggle")) return Material.LEVER;
        if (key.contains("permission")) return Material.TRIPWIRE_HOOK;
        if (key.contains("color")) return Material.CYAN_DYE;
        if (key.contains("name") || key.contains("title")) return Material.NAME_TAG;
        if (key.contains("reward")) return Material.CHEST;
        if (key.contains("chance") || key.contains("weight") || key.contains("multiplier")) return Material.COMPARATOR;
        return Material.WRITABLE_BOOK;
    }

    public static State inferState(String name, List<String> lore, String action) {
        String text = (name + ' ' + String.join(" ", lore)).toLowerCase(Locale.ROOT);
        if (text.contains("locked") || text.contains("unavailable")) return State.LOCKED;
        if (text.contains("current")) return State.CURRENT;
        if (text.contains("completed") || text.contains("claimed")) return State.COMPLETED;
        if (text.contains("active")) return State.ACTIVE;
        if (text.contains("enabled")) return State.ENABLED;
        if (text.contains("disabled") || text.contains("expired")) return State.DISABLED;
        if (text.contains("delete") || text.contains("disband") || text.contains("transfer ownership")) {
            return State.DANGEROUS;
        }
        return action == null || action.equals("none") ? State.STATUS : State.CLICKABLE;
    }

    public static String styledName(String name, State state) {
        String stripped = name == null ? "" : name;
        String color = stripped.startsWith("&") ? "" : state.color();
        if (stripped.contains("&l")) return color + stripped;
        if (stripped.matches("^&[0-9a-fk-or].*")) return stripped.substring(0, 2) + "&l" + stripped.substring(2);
        return color + "&l" + stripped;
    }

    public static List<String> structuredLore(List<String> original, State state, String action) {
        List<String> result = new ArrayList<>();
        for (String line : original) {
            if (line == null) continue;
            if (line.isBlank() && (result.isEmpty() || result.getLast().isBlank())) continue;
            result.add(line);
        }
        if (result.size() > 1 && !result.get(1).isBlank()) result.add(1, "");
        String hint = actionHint(action, state);
        if (!hint.isBlank() && result.stream().noneMatch(line -> line.toLowerCase(Locale.ROOT).contains("click"))) {
            if (!result.isEmpty() && !result.getLast().isBlank()) result.add("");
            result.add(hint);
        }
        return List.copyOf(result);
    }

    public static String actionHint(String action, State state) {
        if (action == null || action.equals("none")) return "";
        if (state == State.LOCKED || action.startsWith("deny:")) return "&cUnavailable.";
        if (state == State.DANGEROUS) return "&cClick to review this dangerous action.";
        if (action.startsWith("execute:")) return "&aClick to confirm.";
        if (action.startsWith("mine:")) return "&eClick to warp.";
        if (action.startsWith("page:") || action.startsWith("editpage|")) return "&eClick to change page.";
        if (action.startsWith("menu:") || action.startsWith("admin:")) return "&eClick to open.";
        if (action.equals("close")) return "&cClick to close.";
        return "&eClick to select.";
    }
}
