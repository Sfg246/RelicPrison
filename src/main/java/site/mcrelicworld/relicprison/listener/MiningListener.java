package site.mcrelicworld.relicprison.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.mining.MiningServiceImpl;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Splits mine block handling into an early immutable prepare stage and a final commit stage.
 * Rewards are committed at HIGHEST after normal protection listeners have had their say; MONITOR
 * only observes and cleans stale preparation contexts because Bukkit MONITOR handlers must not
 * mutate event outcomes.
 */
public final class MiningListener implements Listener {
    private static final String AE_SOURCE = "ADVANCED_ENCHANTMENTS";

    private final RelicPrisonPlugin plugin;
    private final MiningServiceImpl mining;
    private final Map<BlockBreakEvent, MiningServiceImpl.NormalMiningPreparation> prepared =
            new IdentityHashMap<>();

    public MiningListener(RelicPrisonPlugin plugin, MiningServiceImpl mining) {
        this.plugin = plugin;
        this.mining = mining;
    }

    /**
     * Runs before protection and integration plugins. This method may cancel clearly invalid
     * RelicPrison-owned mining attempts, but it must not grant rewards or mutate statistics.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void prepare(BlockBreakEvent event) {
        if (!plugin.isReady() || !plugin.ensureProfileReady(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        String source = plugin.advancedEnchantments().source(event.getBlock());
        MiningServiceImpl.NormalMiningPreparation preparation = mining.prepareNormal(
                event.getPlayer(), event.getBlock(), event.getExpToDrop(), source);
        if (preparation.cancel()) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "mining-cancelled", Map.of("error", preparation.error()));
            return;
        }
        if (preparation.prepared()) {
            prepared.put(event, preparation);
        }
    }

    /**
     * Commits after cancellation-capable LOW through HIGH listeners. RelicPrison does not uncancel
     * another plugin's decision and does not mutate the event from MONITOR.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void commit(BlockBreakEvent event) {
        MiningServiceImpl.NormalMiningPreparation preparation = prepared.remove(event);
        if (preparation == null) return;
        if (event.isCancelled()) return;
        if (!event.getPlayer().isOnline()) return;

        MiningServiceImpl.ProcessResult result = mining.commitNormal(event.getPlayer(), event.getBlock(), preparation);
        if (result.cancel()) {
            event.setCancelled(true);
            if (!result.error().equals("Duplicate block event")) {
                plugin.messages().send(event.getPlayer(), "mining-cancelled", Map.of("error", result.error()));
            }
            return;
        }
        if (result.handled()) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (preparation.source().startsWith(AE_SOURCE)) {
                plugin.advancedEnchantments().consumeSyntheticBlock(event.getBlock());
                event.setCancelled(true);
            }
            return;
        }
        if (result.blocks() > 0 && result.suppressVanillaXp()) {
            event.setExpToDrop(0);
            mining.trackVanilla(event.getPlayer(), event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void monitor(BlockBreakEvent event) {
        prepared.remove(event);
    }
}
