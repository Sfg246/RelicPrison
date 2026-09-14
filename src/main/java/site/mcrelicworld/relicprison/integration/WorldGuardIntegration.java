package site.mcrelicworld.relicprison.integration;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.event.RelicMineCreatedEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineDeletedEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineUpdatedEvent;
import site.mcrelicworld.relicprison.config.WorldGuardConfig;
import site.mcrelicworld.relicprison.mine.MineDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class WorldGuardIntegration implements Listener {
    private final RelicPrisonPlugin plugin;
    private boolean available;
    private Class<?> worldGuardClass;
    private Class<?> bukkitAdapterClass;
    private Class<?> blockVectorClass;
    private Class<?> protectedRegionClass;
    private Class<?> protectedCuboidClass;
    private Class<?> flagsClass;
    private Class<?> stateClass;
    private Class<?> removalStrategyClass;

    public WorldGuardIntegration(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    public void initialize() {
        if (!plugin.config().snapshot().features().worldGuard()) return;
        try {
            worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            blockVectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            protectedCuboidClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion");
            flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            stateClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
            removalStrategyClass = Class.forName("com.sk89q.worldguard.protection.managers.RemovalStrategy");
            available = true;
            plugin.getLogger().info("WorldGuard region integration connected.");
            syncAll();
        } catch (ReflectiveOperationException ex) {
            available = false;
            plugin.getLogger().warning("WorldGuard integration disabled: " + ex.getMessage());
        }
    }


    public void reconfigure() {
        if (!plugin.config().snapshot().features().worldGuard()) return;
        if (!available) {
            initialize();
            return;
        }
        syncAll();
    }

    public void syncAll() {
        if (!enabled() || !plugin.config().snapshot().worldGuard().createRegions()) return;
        for (MineDefinition mine : plugin.mineService().mines()) {
            try { createOrReplace(mine); }
            catch (RuntimeException ex) { plugin.getLogger().warning("Unable to synchronize WorldGuard region for " + mine.id() + ": " + rootMessage(ex)); }
        }
    }

    @EventHandler public void onCreate(RelicMineCreatedEvent event) {
        if (enabled() && plugin.config().snapshot().worldGuard().createRegions()) createOrReplace((MineDefinition) event.mine());
    }

    @EventHandler public void onUpdate(RelicMineUpdatedEvent event) {
        if (!enabled() || !plugin.config().snapshot().worldGuard().updateRegions()) return;
        MineDefinition previous = (MineDefinition) event.previous();
        MineDefinition current = (MineDefinition) event.current();
        if (!previous.id().equals(current.id()) || !previous.worldId().equals(current.worldId())) remove(previous);
        createOrReplace(current);
    }

    @EventHandler public void onDelete(RelicMineDeletedEvent event) {
        if (enabled() && plugin.config().snapshot().worldGuard().deleteRegions()) remove((MineDefinition) event.mine());
    }

    public boolean available() { return available; }
    public String version() {
        org.bukkit.plugin.Plugin dependency = Bukkit.getPluginManager().getPlugin("WorldGuard");
        return dependency == null ? "unavailable" : dependency.getPluginMeta().getVersion();
    }

    private boolean enabled() {
        return available && plugin.config().snapshot().features().worldGuard();
    }


    private void createOrReplace(MineDefinition mine) {
        try {
            World world = Bukkit.getWorld(mine.worldId());
            if (world == null) throw new IllegalStateException("World is not loaded: " + mine.worldName());
            Object manager = regionManager(world);
            String id = regionId(mine.id());
            removeRegion(manager, id);
            Method at = blockVectorClass.getMethod("at", int.class, int.class, int.class);
            Object minimum = at.invoke(null, mine.bounds().minimum().x(), mine.bounds().minimum().y(), mine.bounds().minimum().z());
            Object maximum = at.invoke(null, mine.bounds().maximum().x(), mine.bounds().maximum().y(), mine.bounds().maximum().z());
            Constructor<?> constructor = protectedCuboidClass.getConstructor(String.class, blockVectorClass, blockVectorClass);
            Object region = constructor.newInstance(id, minimum, maximum);
            applyFlags(region, plugin.config().snapshot().worldGuard());
            Method add = manager.getClass().getMethod("addRegion", protectedRegionClass);
            add.invoke(manager, region);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("WorldGuard region creation failed", ex);
        }
    }

    private void remove(MineDefinition mine) {
        try {
            World world = Bukkit.getWorld(mine.worldId());
            if (world == null) return;
            Object manager = regionManager(world);
            removeRegion(manager, regionId(mine.id()));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("WorldGuard region removal failed", ex);
        }
    }

    private Object regionManager(World world) throws ReflectiveOperationException {
        Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
        Object platform = worldGuard.getClass().getMethod("getPlatform").invoke(worldGuard);
        Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
        Object adapted = bukkitAdapterClass.getMethod("adapt", World.class).invoke(null, world);
        for (Method method : container.getClass().getMethods()) {
            if (method.getName().equals("get") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(adapted)) {
                Object manager = method.invoke(container, adapted);
                if (manager == null) throw new IllegalStateException("WorldGuard has no region manager for " + world.getName());
                return manager;
            }
        }
        throw new NoSuchMethodException("RegionContainer#get(World) was not found");
    }

    private void applyFlags(Object region, WorldGuardConfig config) throws ReflectiveOperationException {
        if (config.denyBlockPlace()) setStateFlag(region, "BLOCK_PLACE", "DENY");
        if (config.allowBlockBreak()) setStateFlag(region, "BLOCK_BREAK", "ALLOW");
        if (config.denyExplosions()) {
            setStateFlag(region, "OTHER_EXPLOSION", "DENY");
            setStateFlag(region, "CREEPER_EXPLOSION", "DENY");
            setStateFlag(region, "TNT", "DENY");
        }
        if (config.denyFireSpread()) {
            setStateFlag(region, "FIRE_SPREAD", "DENY");
            setStateFlag(region, "LAVA_FIRE", "DENY");
        }
        if (config.denyFluidFlow()) {
            setStateFlag(region, "WATER_FLOW", "DENY");
            setStateFlag(region, "LAVA_FLOW", "DENY");
        }
    }

    private void setStateFlag(Object region, String fieldName, String stateName) throws ReflectiveOperationException {
        Field field;
        try { field = flagsClass.getField(fieldName); }
        catch (NoSuchFieldException ignored) { return; }
        Object flag = field.get(null);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object state = Enum.valueOf((Class<? extends Enum>) stateClass.asSubclass(Enum.class), stateName);
        for (Method method : protectedRegionClass.getMethods()) {
            if (method.getName().equals("setFlag") && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isInstance(flag)) {
                method.invoke(region, flag, state);
                return;
            }
        }
    }

    private void removeRegion(Object manager, String id) throws ReflectiveOperationException {
        for (Method method : manager.getClass().getMethods()) {
            if (!method.getName().equals("removeRegion")) continue;
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(id)) {
                method.invoke(manager, id);
                return;
            }
            if (method.getParameterCount() == 2 && method.getParameterTypes()[0].isInstance(id)
                    && method.getParameterTypes()[1].isAssignableFrom(removalStrategyClass)) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object strategy = Enum.valueOf((Class<? extends Enum>) removalStrategyClass.asSubclass(Enum.class),
                        "UNSET_PARENT_IN_CHILDREN");
                method.invoke(manager, id, strategy);
                return;
            }
        }
        throw new NoSuchMethodException("removeRegion");
    }

    private String regionId(String mineId) {
        return (plugin.config().snapshot().worldGuard().regionPrefix() + mineId).toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }
}
