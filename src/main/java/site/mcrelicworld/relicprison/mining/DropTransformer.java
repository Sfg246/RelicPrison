package site.mcrelicworld.relicprison.mining;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DropTransformer {
    private DropTransformer() {}

    public static List<ItemStack> smelt(Collection<ItemStack> input, Map<Material, Material> conversions) {
        List<ItemStack> result = new ArrayList<>(input.size());
        for (ItemStack item : input) {
            Material target = conversions.get(item.getType());
            ItemStack copy = item.clone();
            if (target != null) copy = copy.withType(target);
            result.add(copy);
        }
        return List.copyOf(result);
    }

    /** Compresses operation drops using the vanilla 9-to-1 resource-block ratio. */
    public static List<ItemStack> block(Collection<ItemStack> input, Map<Material, Material> conversions) {
        EnumMap<Material, Integer> amounts = new EnumMap<>(Material.class);
        List<ItemStack> passthrough = new ArrayList<>();
        for (ItemStack item : input) {
            if (conversions.containsKey(item.getType())) amounts.merge(item.getType(), item.getAmount(), Integer::sum);
            else passthrough.add(item.clone());
        }
        for (var entry : amounts.entrySet()) {
            Material source = entry.getKey();
            Material target = conversions.get(source);
            int blocks = entry.getValue() / 9;
            int remainder = entry.getValue() % 9;
            addStacks(passthrough, target, blocks);
            addStacks(passthrough, source, remainder);
        }
        return List.copyOf(passthrough);
    }

    public static List<ItemStack> consolidate(Collection<ItemStack> input) {
        EnumMap<Material, Integer> amounts = new EnumMap<>(Material.class);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : input) {
            if (item == null || item.getAmount() <= 0 || item.getType().isAir()) continue;
            if (item.hasItemMeta()) result.add(item.clone());
            else amounts.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        for (var entry : amounts.entrySet()) addStacks(result, entry.getKey(), entry.getValue());
        return List.copyOf(result);
    }

    private static void addStacks(List<ItemStack> output, Material material, int amount) {
        if (material == null || amount <= 0) return;
        int max = material.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int size = Math.min(max, remaining);
            output.add(new ItemStack(material, size));
            remaining -= size;
        }
    }
}
