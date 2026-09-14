package site.mcrelicworld.relicprison.integration;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.api.model.BlockPosition;
import site.mcrelicworld.relicprison.mine.Cuboid;

import java.lang.reflect.Method;
import java.util.Optional;

public final class WorldEditSelectionProvider {
    public record Selection(World world, Cuboid cuboid) {}

    public boolean available() {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
    }

    public String providerName() {
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null) return "FastAsyncWorldEdit";
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null) return "WorldEdit";
        return "unavailable";
    }

    public String version() {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(providerName());
        return plugin == null ? "unavailable" : plugin.getPluginMeta().getVersion();
    }

    public Optional<Selection> selection(Player player) {
        if (!available()) return Optional.empty();
        try {
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object actor = adapterClass.getMethod("adapt", Player.class).invoke(null, player);
            Class<?> worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object worldEdit = worldEditClass.getMethod("getInstance").invoke(null);
            Object sessionManager = worldEdit.getClass().getMethod("getSessionManager").invoke(worldEdit);
            Method getSession = findCompatible(sessionManager.getClass(), "get", actor.getClass());
            Object localSession = getSession.invoke(sessionManager, actor);
            Object selectionWorld = localSession.getClass().getMethod("getSelectionWorld").invoke(localSession);
            Object region;
            try {
                region = localSession.getClass().getMethod("getSelection", selectionWorld.getClass().getInterfaces().length > 0
                        ? selectionWorld.getClass().getInterfaces()[0] : selectionWorld.getClass()).invoke(localSession, selectionWorld);
            } catch (ReflectiveOperationException firstFailure) {
                Method selectionMethod = null;
                for (Method candidate : localSession.getClass().getMethods()) {
                    if (candidate.getName().equals("getSelection") && candidate.getParameterCount() == 1
                            && candidate.getParameterTypes()[0].isAssignableFrom(selectionWorld.getClass())) {
                        selectionMethod = candidate;
                        break;
                    }
                }
                if (selectionMethod == null) region = localSession.getClass().getMethod("getSelection").invoke(localSession);
                else region = selectionMethod.invoke(localSession, selectionWorld);
            }
            String worldName = String.valueOf(selectionWorld.getClass().getMethod("getName").invoke(selectionWorld));
            World bukkitWorld = Bukkit.getWorld(worldName);
            if (bukkitWorld == null) return Optional.empty();
            Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);
            BlockPosition minimum = vector(min);
            BlockPosition maximum = vector(max);
            return Optional.of(new Selection(bukkitWorld, new Cuboid(minimum, maximum)));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static Method findCompatible(Class<?> type, String name, Class<?> argument) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(argument)) return method;
        }
        throw new NoSuchMethodException(type.getName() + '#' + name);
    }

    private static BlockPosition vector(Object vector) throws ReflectiveOperationException {
        Class<?> type = vector.getClass();
        int x = ((Number) type.getMethod("getBlockX").invoke(vector)).intValue();
        int y = ((Number) type.getMethod("getBlockY").invoke(vector)).intValue();
        int z = ((Number) type.getMethod("getBlockZ").invoke(vector)).intValue();
        return new BlockPosition(x, y, z);
    }
}
