package site.mcrelicworld.relicprison.mining.provider;

import org.bukkit.block.Block;

import java.util.Collection;
import java.util.List;

public interface BulkMiningProvider {
    String id();
    ProviderCapabilities capabilities();
    default List<Block> normalize(Collection<Block> blocks) {
        return blocks == null ? List.of() : List.copyOf(blocks);
    }
}
