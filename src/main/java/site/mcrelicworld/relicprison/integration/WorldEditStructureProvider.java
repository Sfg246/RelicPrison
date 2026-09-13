package site.mcrelicworld.relicprison.integration;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import site.mcrelicworld.relicprison.mine.Cuboid;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reflection-only WorldEdit/FAWE adapter so both integrations remain optional runtime dependencies. */
public final class WorldEditStructureProvider {
    public boolean available() {
        return enabledPlugin("FastAsyncWorldEdit") || enabledPlugin("WorldEdit");
    }

    public String providerName() {
        if (enabledPlugin("FastAsyncWorldEdit")) return "FAWE";
        if (enabledPlugin("WorldEdit")) return "WORLDEDIT";
        return "UNAVAILABLE";
    }

    public void copy(World sourceWorld, Cuboid sourceBounds, World destinationWorld, Cuboid destinationBounds)
            throws ReflectiveOperationException {
        Object worldEdit = worldEdit();
        Object source = adapt(sourceWorld);
        Object destination = adapt(destinationWorld);
        Object sourceMinimum = vector(sourceBounds.minimum().x(), sourceBounds.minimum().y(), sourceBounds.minimum().z());
        Object sourceMaximum = vector(sourceBounds.maximum().x(), sourceBounds.maximum().y(), sourceBounds.maximum().z());
        Object destinationMinimum = vector(destinationBounds.minimum().x(), destinationBounds.minimum().y(),
                destinationBounds.minimum().z());
        Object region = construct(Class.forName("com.sk89q.worldedit.regions.CuboidRegion"),
                source, sourceMinimum, sourceMaximum);
        Object clipboard = construct(Class.forName("com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard"), region);
        Object forwardCopy = construct(Class.forName("com.sk89q.worldedit.function.operation.ForwardExtentCopy"),
                source, region, sourceMinimum, clipboard, sourceMinimum);
        // Mines copy blocks and block entities only. Copying entities can duplicate mobs, displays, or players.
        invokeIfPresent(forwardCopy, "setCopyingEntities", false);
        invokeIfPresent(forwardCopy, "setCopyingBiomes", false);
        complete(forwardCopy);

        Object editSession = invoke(worldEdit, "newEditSession", destination);
        try {
            Object holder = construct(Class.forName("com.sk89q.worldedit.session.ClipboardHolder"), clipboard);
            Object paste = invoke(holder, "createPaste", editSession);
            paste = invoke(paste, "to", destinationMinimum);
            paste = invoke(paste, "ignoreAirBlocks", false);
            paste = invokeIfPresentReturning(paste, "copyEntities", false);
            paste = invokeIfPresentReturning(paste, "copyBiomes", false);
            Object operation = invoke(paste, "build");
            complete(operation);
        } finally {
            close(editSession);
        }
    }

    public void clear(World world, Cuboid bounds) throws ReflectiveOperationException {
        Object worldEdit = worldEdit();
        Object adaptedWorld = adapt(world);
        Object minimum = vector(bounds.minimum().x(), bounds.minimum().y(), bounds.minimum().z());
        Object maximum = vector(bounds.maximum().x(), bounds.maximum().y(), bounds.maximum().z());
        Object region = construct(Class.forName("com.sk89q.worldedit.regions.CuboidRegion"),
                adaptedWorld, minimum, maximum);
        Object editSession = invoke(worldEdit, "newEditSession", adaptedWorld);
        try {
            Class<?> blockTypes = Class.forName("com.sk89q.worldedit.world.block.BlockTypes");
            Field airField = blockTypes.getField("AIR");
            Object airType = airField.get(null);
            Object airState = invoke(airType, "getDefaultState");
            invoke(editSession, "setBlocks", region, airState);
        } finally {
            close(editSession);
        }
    }

    private static boolean enabledPlugin(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    private static Object worldEdit() throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.sk89q.worldedit.WorldEdit");
        return type.getMethod("getInstance").invoke(null);
    }

    private static Object adapt(World world) throws ReflectiveOperationException {
        Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        return adapter.getMethod("adapt", World.class).invoke(null, world);
    }

    private static Object vector(int x, int y, int z) throws ReflectiveOperationException {
        Class<?> vector = Class.forName("com.sk89q.worldedit.math.BlockVector3");
        return vector.getMethod("at", int.class, int.class, int.class).invoke(null, x, y, z);
    }

    private static void complete(Object operation) throws ReflectiveOperationException {
        Class<?> operations = Class.forName("com.sk89q.worldedit.function.operation.Operations");
        Method selected = null;
        for (Method method : operations.getMethods()) {
            if ((method.getName().equals("complete") || method.getName().equals("completeLegacy"))
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(operation.getClass())) {
                selected = method;
                break;
            }
        }
        if (selected == null) throw new NoSuchMethodException("WorldEdit Operations.complete(Operation)");
        selected.invoke(null, operation);
    }

    private static Object construct(Class<?> type, Object... arguments) throws ReflectiveOperationException {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == arguments.length
                    && compatible(constructor.getParameterTypes(), arguments)) {
                return constructor.newInstance(arguments);
            }
        }
        throw new NoSuchMethodException("No compatible constructor for " + type.getName());
    }

    private static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name, arguments);
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw ex;
        }
    }

    private static void invokeIfPresent(Object target, String name, Object... arguments)
            throws ReflectiveOperationException {
        Method method = findMethodOrNull(target.getClass(), name, arguments);
        if (method != null) method.invoke(target, arguments);
    }

    private static Object invokeIfPresentReturning(Object target, String name, Object... arguments)
            throws ReflectiveOperationException {
        Method method = findMethodOrNull(target.getClass(), name, arguments);
        if (method == null) return target;
        Object result = method.invoke(target, arguments);
        return result == null ? target : result;
    }

    private static Method findMethod(Class<?> type, String name, Object[] arguments) throws NoSuchMethodException {
        Method method = findMethodOrNull(type, name, arguments);
        if (method == null) throw new NoSuchMethodException(type.getName() + '#' + name);
        return method;
    }

    private static Method findMethodOrNull(Class<?> type, String name, Object[] arguments) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments.length
                    && compatible(method.getParameterTypes(), arguments)) {
                return method;
            }
        }
        return null;
    }

    private static boolean compatible(Class<?>[] parameters, Object[] arguments) {
        for (int index = 0; index < parameters.length; index++) {
            Object argument = arguments[index];
            if (argument != null && !box(parameters[index]).isInstance(argument)) return false;
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static void close(Object closeable) {
        if (closeable == null) return;
        try {
            invoke(closeable, "close");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // WorldEdit sessions are still reclaimed by the integration if close is unavailable.
        }
    }
}
