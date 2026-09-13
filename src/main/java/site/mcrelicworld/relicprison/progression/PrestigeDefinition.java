package site.mcrelicworld.relicprison.progression;

import site.mcrelicworld.relicprison.api.model.PrestigeView;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;

public record PrestigeDefinition(
        String id,
        String displayName,
        int order,
        BigDecimal cost,
        BigDecimal sellMultiplier,
        BigDecimal rankCostMultiplier,
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
    public PrestigeDefinition(String id, String displayName, int order, BigDecimal cost,
                              BigDecimal sellMultiplier, BigDecimal rankCostMultiplier,
                              String mineId, String luckPermsGroup, List<String> enterCommands,
                              List<String> leaveCommands) {
        this(id, displayName, order, cost, sellMultiplier, rankCostMultiplier, mineId,
                luckPermsGroup, enterCommands, leaveCommands, true, null, Material.NETHER_STAR,
                List.of(), List.of());
    }

    public PrestigeDefinition {
        id = normalize(id);
        displayName = Objects.requireNonNullElse(displayName, id);
        if (order < 0) throw new IllegalArgumentException("Prestige order cannot be negative");
        Objects.requireNonNull(cost);
        Objects.requireNonNull(sellMultiplier);
        Objects.requireNonNull(rankCostMultiplier);
        if (cost.signum() < 0 || sellMultiplier.signum() <= 0 || rankCostMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("Prestige numeric values must be nonnegative and multipliers positive");
        }
        mineId = mineId == null || mineId.isBlank() ? null : mineId.toLowerCase(java.util.Locale.ROOT);
        luckPermsGroup = Objects.requireNonNull(luckPermsGroup).toLowerCase(java.util.Locale.ROOT);
        if (!luckPermsGroup.matches("[a-z0-9_.-]{1,128}")) {
            throw new IllegalArgumentException("Invalid LuckPerms group: " + luckPermsGroup);
        }
        enterCommands = List.copyOf(enterCommands);
        leaveCommands = List.copyOf(leaveCommands);
        permission = normalizePermission(permission);
        displayMaterial = Objects.requireNonNullElse(displayMaterial, Material.NETHER_STAR);
        if (!displayMaterial.isItem()) throw new IllegalArgumentException("Prestige display material must be an item");
        lore = List.copyOf(Objects.requireNonNullElse(lore, List.of()));
        requirements = List.copyOf(Objects.requireNonNullElse(requirements, List.of())).stream()
                .map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    public PrestigeView view() {
        return new PrestigeView(id, displayName, order, cost, sellMultiplier, rankCostMultiplier, mineId);
    }

    public static String normalize(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid prestige ID: " + value);
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizePermission(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]{1,128}")) throw new IllegalArgumentException("Invalid prestige permission");
        return normalized;
    }
}
