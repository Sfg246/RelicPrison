package site.mcrelicworld.relicprison.mining;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DropTransformerTest {
    @Test void smeltsAndCompressesWithoutLosingRemainders() {
        List<ItemStack> smelted = DropTransformer.smelt(
                List.of(new ItemStack(Material.RAW_IRON, 5)),
                Map.of(Material.RAW_IRON, Material.IRON_INGOT));
        assertEquals(Material.IRON_INGOT, smelted.getFirst().getType());
        assertEquals(5, smelted.getFirst().getAmount());

        List<ItemStack> blocked = DropTransformer.block(
                List.of(new ItemStack(Material.IRON_INGOT, 20)),
                Map.of(Material.IRON_INGOT, Material.IRON_BLOCK));
        assertEquals(2, blocked.stream()
                .filter(item -> item.getType() == Material.IRON_BLOCK)
                .mapToInt(ItemStack::getAmount).sum());
        assertEquals(2, blocked.stream()
                .filter(item -> item.getType() == Material.IRON_INGOT)
                .mapToInt(ItemStack::getAmount).sum());
    }

    @Test void consolidationPreservesTotalAmount() {
        List<ItemStack> result = DropTransformer.consolidate(List.of(
                new ItemStack(Material.COAL, 40),
                new ItemStack(Material.COAL, 40)));
        assertEquals(80, result.stream().mapToInt(ItemStack::getAmount).sum());
    }
}
