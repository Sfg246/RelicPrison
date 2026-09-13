package site.mcrelicworld.relicprison.selling;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SellSummaryService {
    private final RelicPrisonPlugin plugin;
    private volatile int summarySeconds;
    private final Map<UUID, BigDecimal> pending = new ConcurrentHashMap<>();
    private int taskId = -1;

    public SellSummaryService(RelicPrisonPlugin plugin, int summarySeconds) {
        this.plugin = plugin; this.summarySeconds = Math.max(1, summarySeconds);
    }
    public void initialize() { schedule(); }
    public void reconfigure(int seconds) {
        summarySeconds = Math.max(1, seconds);
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        flush();
        schedule();
    }
    private void schedule() {
        long ticks = summarySeconds * 20L;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::flush, ticks, ticks);
    }
    public void shutdown() { if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); flush(); }
    public void add(UUID playerId, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) pending.merge(playerId, amount, BigDecimal::add);
    }
    public void flush() {
        Map<UUID, BigDecimal> copy = Map.copyOf(pending);
        copy.keySet().forEach(pending::remove);
        for (var entry : copy.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) plugin.messages().send(player, "autosell-summary",
                    Map.of("amount", plugin.numbers().currency(entry.getValue().doubleValue())));
        }
    }
}
