package site.mcrelicworld.relicprison.mining;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InventoryAutoBlockConverterTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 8, 9, 10, 17, 18, 63, 64, 65, 511})
    void planPreservesExactRemainders(int amount) {
        ItemStack[] inventory = amount == 0 ? new ItemStack[0]
                : new ItemStack[] {new ItemStack(Material.DIAMOND, Math.min(amount, 64))};
        int newAmount = Math.max(0, amount - Math.min(amount, 64));
        List<ItemStack> drops = newAmount == 0 ? List.of() : List.of(new ItemStack(Material.DIAMOND, newAmount));

        InventoryAutoBlockConverter.Conversion conversion = InventoryAutoBlockConverter.plan(inventory, drops,
                Map.of(Material.DIAMOND, Material.DIAMOND_BLOCK));

        int blocks = count(conversion.output(), Material.DIAMOND_BLOCK);
        int remainder = count(conversion.output(), Material.DIAMOND);
        assertEquals(amount / 9, blocks);
        assertEquals(amount % 9, remainder);
    }

    private static int count(List<ItemStack> items, Material material) {
        return items.stream().filter(item -> item.getType() == material).mapToInt(ItemStack::getAmount).sum();
    }
}
