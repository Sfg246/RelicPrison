package site.mcrelicworld.relicprison.apitest;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import site.mcrelicworld.relicprison.api.RelicPrisonApi;
import site.mcrelicworld.relicprison.api.event.RelicBoosterActivateEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineResetCompleteEvent;
import site.mcrelicworld.relicprison.api.event.RelicPlayerProgressionRepairEvent;
import site.mcrelicworld.relicprison.api.event.RelicSellEvent;

import java.util.UUID;

public final class RelicPrisonApiTestPlugin extends JavaPlugin implements Listener {
    @Override public void onEnable() {
        RegisteredServiceProvider<RelicPrisonApi> registration =
                Bukkit.getServicesManager().getRegistration(RelicPrisonApi.class);
        if (registration == null) throw new IllegalStateException("RelicPrisonApi service is not registered");
        RelicPrisonApi api = registration.getProvider();
        api.mines().mines();
        api.progression().currentRank(new UUID(0L, 0L));
        api.selling();
        api.multipliers();
        api.boosters().activeServerBoosters();
        api.statistics();
        api.numbers().currency(0.0D);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler public void onSell(RelicSellEvent event) { event.finalValue(); }
    @EventHandler public void onBooster(RelicBoosterActivateEvent event) { event.multiplier(); }
    @EventHandler public void onReset(RelicMineResetCompleteEvent event) { event.durationMillis(); }
    @EventHandler public void onRepair(RelicPlayerProgressionRepairEvent event) { event.repairsPerformed(); }
}
