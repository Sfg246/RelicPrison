package site.mcrelicworld.relicprison.mining;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class FortuneCalculator {
    private static final Set<Material> ELIGIBLE = EnumSet.noneOf(Material.class);
    static {
        for (String name : List.of("COAL_ORE", "DEEPSLATE_COAL_ORE", "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE",
                "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE", "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE",
                "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE",
                "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "GLOWSTONE", "GRAVEL")) {
            Material material = Material.matchMaterial(name);
            if (material != null) ELIGIBLE.add(material);
        }
    }
    private FortuneCalculator() {}

    public static Collection<ItemStack> apply(Material source, Collection<ItemStack> base, int level,
                                               int maximumLevel, double levelMultiplier,
                                               Set<Material> excluded) {
        if (level <= 0 || excluded.contains(source) || !ELIGIBLE.contains(source)) return List.copyOf(base);
        int effective = Math.min(maximumLevel, Math.max(0, (int) Math.round(level * levelMultiplier)));
        if (effective <= 0) return List.copyOf(base);
        int bonus = Math.max(0, ThreadLocalRandom.current().nextInt(effective + 2) - 1);
        int factor = 1 + bonus;
        List<ItemStack> result = new ArrayList<>(base.size());
        for (ItemStack item : base) {
            ItemStack copy = item.clone();
            long amount = (long) copy.getAmount() * factor;
            copy.setAmount((int) Math.min(copy.getMaxStackSize(), amount));
            result.add(copy);
            long remaining = amount - copy.getAmount();
            while (remaining > 0) {
                ItemStack extra = item.clone();
                extra.setAmount((int) Math.min(extra.getMaxStackSize(), remaining));
                result.add(extra);
                remaining -= extra.getAmount();
            }
        }
        return List.copyOf(result);
    }
}
