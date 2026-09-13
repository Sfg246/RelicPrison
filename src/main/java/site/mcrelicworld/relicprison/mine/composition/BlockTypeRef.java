package site.mcrelicworld.relicprison.mine.composition;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Objects;

public record BlockTypeRef(Kind kind, String id, Material material) {
    public enum Kind { VANILLA, ITEMSADDER, AIR }

    public BlockTypeRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (kind == Kind.VANILLA && material == null) throw new IllegalArgumentException("Vanilla block requires a Material");
    }

    public static BlockTypeRef vanilla(Material material) {
        if (!material.isBlock()) throw new IllegalArgumentException(material + " is not a block");
        return new BlockTypeRef(Kind.VANILLA, material.name(), material);
    }

    public static BlockTypeRef air() {
        return new BlockTypeRef(Kind.AIR, "air", Material.AIR);
    }

    public static BlockTypeRef parse(String raw) {
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("air") || lower.equals("vanilla:air") || lower.equals("minecraft:air")) return air();
        if (lower.startsWith("itemsadder:")) {
            return new BlockTypeRef(Kind.ITEMSADDER, lower.substring("itemsadder:".length()), null);
        }
        if (lower.startsWith("vanilla:")) value = value.substring("vanilla:".length());
        else if (lower.startsWith("minecraft:")) value = value.substring("minecraft:".length());
        Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
        if (material != null && material.isBlock()) return vanilla(material);
        if (value.contains(":")) return new BlockTypeRef(Kind.ITEMSADDER, value.toLowerCase(Locale.ROOT), null);
        throw new IllegalArgumentException("Unknown block type: " + raw);
    }

    public String provider() {
        return switch (kind) {
            case VANILLA, AIR -> "vanilla";
            case ITEMSADDER -> "itemsadder";
        };
    }

    public String qualifiedId() {
        return provider() + ":" + id.toLowerCase(Locale.ROOT);
    }

    public boolean airBlock() {
        return kind == Kind.AIR;
    }
}
