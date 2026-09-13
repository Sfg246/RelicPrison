package site.mcrelicworld.relicprison.mining.provider;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public interface CustomBlockProvider {
    String id();
    ProviderCapabilities capabilities();
    Optional<String> blockId(Block block);
    List<ItemStack> blockLoot(Block block, ItemStack tool);
    boolean removeBlock(Location location);
    default boolean restoreBlock(Location location, String customBlockId, BlockData fallbackBlockData) {
        if (fallbackBlockData == null) return false;
        location.getBlock().setBlockData(fallbackBlockData, false);
        return true;
    }
}
