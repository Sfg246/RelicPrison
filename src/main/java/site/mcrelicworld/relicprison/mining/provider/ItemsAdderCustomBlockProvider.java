package site.mcrelicworld.relicprison.mining.provider;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import site.mcrelicworld.relicprison.integration.ItemsAdderIntegration;

import java.util.List;
import java.util.Optional;

public final class ItemsAdderCustomBlockProvider implements CustomBlockProvider {
    private static final ProviderCapabilities CONNECTED =
            new ProviderCapabilities(false, true, true, false);
    private static final ProviderCapabilities DISCONNECTED =
            new ProviderCapabilities(false, false, false, false);

    private final ItemsAdderIntegration integration;

    public ItemsAdderCustomBlockProvider(ItemsAdderIntegration integration) {
        this.integration = integration;
    }

    @Override public String id() { return "itemsadder"; }

    @Override public ProviderCapabilities capabilities() {
        return integration != null && integration.enabled() ? CONNECTED : DISCONNECTED;
    }

    @Override public Optional<String> blockId(Block block) {
        return integration == null || !integration.enabled() ? Optional.empty() : integration.customBlockId(block);
    }

    @Override public List<ItemStack> blockLoot(Block block, ItemStack tool) {
        return integration == null || !integration.enabled() ? List.of() : integration.blockLoot(block, tool);
    }

    @Override public boolean removeBlock(Location location) {
        return integration != null && integration.enabled() && integration.removeBlock(location);
    }

    @Override public boolean restoreBlock(Location location, String customBlockId, BlockData fallbackBlockData) {
        if (integration != null && integration.enabled() && customBlockId != null && !customBlockId.isBlank()
                && integration.placeBlock(customBlockId, location)) {
            return true;
        }
        if (fallbackBlockData == null) return false;
        location.getBlock().setBlockData(fallbackBlockData, false);
        return true;
    }
}
