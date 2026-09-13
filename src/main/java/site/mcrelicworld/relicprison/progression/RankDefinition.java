package site.mcrelicworld.relicprison.progression;

import site.mcrelicworld.relicprison.api.model.RankView;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;

public record RankDefinition(
        String id,
        String displayName,
        int order,
        BigDecimal nextCost,
        BigDecimal sellMultiplier,
        String mineId,
        String luckPermsGroup,
        List<String> enterCommands,
        List<String> leaveCommands,
        boolean enabled,
        String permission,
        Material displayMaterial,
        List<String> lore,
        List<String> requirements
) {
    public RankDefinition(String id, String displayName, int order, BigDecimal nextCost,
                          BigDecimal sellMultiplier, String mineId, String luckPermsGroup,
                          List<String> enterCommands, List<String> leaveCommands) {
        this(id, displayName, order, nextCost, sellMultiplier, mineId, luckPermsGroup,
                enterCommands, leaveCommands, true, null, Material.EXPERIENCE_BOTTLE, List.of(), List.of());
    }

    public RankDefinition {
        id = normalize(id);
        displayName = Objects.requireNonNullElse(displayName, id.toUpperCase(java.util.Locale.ROOT));
        if (order < 0) throw new IllegalArgumentException("Rank order cannot be negative");
        Objects.requireNonNull(nextCost);
        Objects.requireNonNull(sellMultiplier);
        if (sellMultiplier.signum() <= 0) throw new IllegalArgumentException("Rank sell multiplier must be positive");
        if (nextCost.signum() < 0) throw new IllegalArgumentException("Rank cost cannot be negative");
        mineId = mineId == null || mineId.isBlank() ? null : mineId.toLowerCase(java.util.Locale.ROOT);
        luckPermsGroup = Objects.requireNonNull(luckPermsGroup).toLowerCase(java.util.Locale.ROOT);
        if (!luckPermsGroup.matches("[a-z0-9_.-]{1,128}")) {
            throw new IllegalArgumentException("Invalid LuckPerms group: " + luckPermsGroup);
        }
        enterCommands = List.copyOf(enterCommands);
        leaveCommands = List.copyOf(leaveCommands);
        permission = normalizePermission(permission);
        displayMaterial = Objects.requireNonNullElse(displayMaterial, Material.EXPERIENCE_BOTTLE);
        if (!displayMaterial.isItem()) throw new IllegalArgumentException("Rank display material must be an item");
        lore = List.copyOf(Objects.requireNonNullElse(lore, List.of()));
        requirements = List.copyOf(Objects.requireNonNullElse(requirements, List.of())).stream()
                .map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    public RankView view() { return new RankView(id, displayName, order, nextCost, mineId); }

    public static String normalize(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid rank ID: " + value);
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizePermission(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]{1,128}")) throw new IllegalArgumentException("Invalid rank permission");
        return normalized;
    }
}
