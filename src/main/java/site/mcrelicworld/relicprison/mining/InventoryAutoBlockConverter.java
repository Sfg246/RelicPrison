package site.mcrelicworld.relicprison.mining;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class InventoryAutoBlockConverter {
    private InventoryAutoBlockConverter() { }

    public static Result convert(PlayerInventory inventory, Collection<ItemStack> newDrops,
                                 Map<Material, Material> conversions) {
        ItemStack[] before = cloneContents(inventory.getStorageContents());
        try {
            Conversion conversion = plan(before, newDrops, conversions);
            ItemStack[] empty = new ItemStack[before.length];
            inventory.setStorageContents(empty);
            List<ItemStack> toAdd = new ArrayList<>(conversion.keptInventory());
            toAdd.addAll(conversion.output());
            Map<Integer, ItemStack> overflow = inventory.addItem(toAdd.toArray(ItemStack[]::new));
            if (!overflow.isEmpty()) {
                inventory.setStorageContents(before);
                return Result.failure("Inventory cannot fit AutoBlock conversion output");
            }
            return Result.success();
        } catch (RuntimeException ex) {
            inventory.setStorageContents(before);
            return Result.failure(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    static Conversion plan(ItemStack[] inventoryContents, Collection<ItemStack> newDrops,
                           Map<Material, Material> conversions) {
        EnumMap<Material, Integer> sourceAmounts = new EnumMap<>(Material.class);
        List<ItemStack> kept = new ArrayList<>();
        List<ItemStack> output = new ArrayList<>();
        for (ItemStack item : inventoryContents) {
            if (item == null || item.getAmount() <= 0 || item.getType().isAir()) continue;
            if (!item.hasItemMeta() && conversions.containsKey(item.getType())) {
                sourceAmounts.merge(item.getType(), item.getAmount(), Integer::sum);
            } else {
                kept.add(item.clone());
            }
        }
        for (ItemStack item : newDrops) {
            if (item == null || item.getAmount() <= 0 || item.getType().isAir()) continue;
            if (!item.hasItemMeta() && conversions.containsKey(item.getType())) {
                sourceAmounts.merge(item.getType(), item.getAmount(), Integer::sum);
            } else {
                output.add(item.clone());
            }
        }
        for (var entry : sourceAmounts.entrySet()) {
            Material source = entry.getKey();
            Material target = conversions.get(source);
            int amount = entry.getValue();
            int blocks = amount / 9;
            int remainder = amount % 9;
            addStacks(output, target, blocks);
            addStacks(output, source, remainder);
        }
        return new Conversion(List.copyOf(kept), DropTransformer.consolidate(output));
    }

    private static void addStacks(List<ItemStack> output, Material material, int amount) {
        if (material == null || amount <= 0) return;
        int maxStack = material.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int size = Math.min(maxStack, remaining);
            output.add(new ItemStack(material, size));
            remaining -= size;
        }
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copy;
    }

    public record Result(boolean successful, String error) {
        static Result success() { return new Result(true, ""); }
        static Result failure(String error) { return new Result(false, error); }
    }

    record Conversion(List<ItemStack> keptInventory, List<ItemStack> output) { }
}
