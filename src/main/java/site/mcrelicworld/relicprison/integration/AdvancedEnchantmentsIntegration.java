package site.mcrelicworld.relicprison.integration;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime bridge for AdvancedEnchantments 9.22.9.
 *
 * <p>RelicPrison processes the original Bukkit break event before AE's HIGH-priority mining
 * listener. AE then emits marked synthetic BlockBreakEvents for additional Drill/Trench blocks.
 * RelicPrison processes those synthetic blocks, removes them itself, and cancels the synthetic
 * event so AE removes the matching pending drop entry. The original drop entry is removed from
 * AE's DropsHandler only after RelicPrison confirms that it already handled that coordinate.</p>
 *
 * <p>AE's add-to-inventory setting is also enabled as a safety fallback for any effect-specific
 * block which does not emit a synthetic break event. This guarantees that AE cannot leave mine
 * drops on the floor while server AutoPickup is enabled.</p>
 */
public final class AdvancedEnchantmentsIntegration {
    private static final long ORIGINAL_MARK_MILLIS = 2_000L;
    private static final long ERROR_LOG_INTERVAL_MILLIS = 10_000L;

    private final RelicPrisonPlugin plugin;
    private final Listener runtimeListener = new Listener() { };
    private final Map<CoordinateKey, Long> handledOriginals = new ConcurrentHashMap<>();
    private final Map<Integer, Long> handledBulkEvents = new ConcurrentHashMap<>();
    private final AtomicLong lastBridgeErrorLog = new AtomicLong();

    private volatile Plugin advancedEnchantments;
    private volatile Method setIgnoreBlockEvent;
    private volatile boolean bulkBridgeActive;

    public AdvancedEnchantmentsIntegration(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Reloads must replace the reflective bridge instead of stacking duplicate listeners.
        HandlerList.unregisterAll(runtimeListener);
        bulkBridgeActive = false;
        advancedEnchantments = plugin.getServer().getPluginManager().getPlugin("AdvancedEnchantments");
        if (advancedEnchantments == null || !advancedEnchantments.isEnabled()) {
            advancedEnchantments = null;
            setIgnoreBlockEvent = null;
            bulkBridgeActive = false;
            if (plugin.config().snapshot().features().advancedEnchantments()) {
                plugin.getLogger().warning("AdvancedEnchantments integration enabled, but the plugin is not installed.");
            }
            return;
        }

        ClassLoader loader = advancedEnchantments.getClass().getClassLoader();
        try {
            Class<?> api = Class.forName("net.advancedplugins.ae.api.AEAPI", false, loader);
            setIgnoreBlockEvent = api.getMethod("setIgnoreBlockEvent", Block.class);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("AdvancedEnchantments " + advancedEnchantments.getDescription().getVersion()
                    + " was found, but AEAPI.setIgnoreBlockEvent(Block) is unavailable.");
        }

        try {
            registerRuntimeEvent(loader,
                    "net.advancedplugins.ae.impl.effects.api.EffectsActivatedEvent",
                    this::handleEffectsActivated);
            bulkBridgeActive = true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            bulkBridgeActive = false;
            plugin.getLogger().warning("AdvancedEnchantments synthetic mining bridge could not be registered: "
                    + rootMessage(ex));
        }

        plugin.getLogger().info("AdvancedEnchantments connected: "
                + advancedEnchantments.getDescription().getVersion()
                + ". Synthetic mining bridge=" + bulkBridgeActive + '.');
    }

    @SuppressWarnings("unchecked")
    private void registerRuntimeEvent(ClassLoader loader, String eventClassName, EventExecutor executor)
            throws ReflectiveOperationException {
        Class<?> raw = Class.forName(eventClassName, false, loader);
        if (!Event.class.isAssignableFrom(raw)) {
            throw new IllegalStateException(eventClassName + " is not a Bukkit event");
        }
        Class<? extends Event> eventType = (Class<? extends Event>) raw;
        plugin.getServer().getPluginManager().registerEvent(
                eventType, runtimeListener, EventPriority.MONITOR, executor, plugin, true);
    }

    private void handleEffectsActivated(Listener ignored, Event event) {
        if (!plugin.config().snapshot().features().advancedEnchantments() || !connected()) return;
        try {
            Object task = invoke(event, "getExecutionTask");
            Object builder = invoke(task, "getBuilder");
            Object dropsHandler = invoke(builder, "getDrops");
            Object settings = invoke(dropsHandler, "getSettings");
            if (!booleanValue(invoke(settings, "isBreakBlocks"))) return;

            var features = plugin.config().snapshot().features();
            if (plugin.miningService().ownsDropsGlobally()) {
                // Keep AE's fallback behavior aligned with the same global switches used by RelicPrison.
                invoke(settings, "setAddToInventory", features.autoPickup());
                invoke(settings, "setSmelt", features.autoSmelt());
            }
            if (features.miningXp()) {
                invoke(settings, "setDropExp", false);
                invoke(settings, "setDropExpAmount", 0);
            }

            Block original = originalBlock(builder);
            if (original != null && wasOriginalHandled(original)) {
                invoke(dropsHandler, "removeBlock", original);
            }
            processBulkEventOnce(event, builder, dropsHandler, original);
        } catch (Throwable error) {
            logBridgeError("AdvancedEnchantments synthetic mining bridge failed", error);
        }
    }

    private void processBulkEventOnce(Event event, Object builder, Object dropsHandler, Block original)
            throws ReflectiveOperationException {
        if (plugin.miningService() == null) return;
        BlockBreakEvent breakEvent = originalBreakEvent(builder);
        if (breakEvent == null || breakEvent.isCancelled()) return;
        int eventKey = System.identityHashCode(event);
        long now = System.currentTimeMillis();
        handledBulkEvents.entrySet().removeIf(entry -> now - entry.getValue() > ORIGINAL_MARK_MILLIS);
        if (handledBulkEvents.putIfAbsent(eventKey, now) != null) return;
        List<Block> blocks = reflectedBlocks(dropsHandler);
        if (blocks.size() <= 1) return;
        if (original != null) {
            CoordinateKey originalKey = key(original);
            blocks = blocks.stream().filter(block -> !key(block).equals(originalKey)).toList();
        }
        if (blocks.isEmpty()) return;
        var result = plugin.miningService().processBulk(breakEvent.getPlayer(), blocks, "ADVANCED_ENCHANTMENTS");
        if (!result.success()) {
            logBridgeError("AdvancedEnchantments bulk operation rejected: " + result.error(), null);
            return;
        }
        for (Block block : blocks) {
            try {
                invoke(dropsHandler, "removeBlock", block);
            } catch (ReflectiveOperationException ignored) {
                return;
            }
        }
    }

    private static BlockBreakEvent originalBreakEvent(Object builder) throws ReflectiveOperationException {
        Object originalEvent = invoke(builder, "getEvent");
        return originalEvent instanceof BlockBreakEvent breakEvent ? breakEvent : null;
    }

    private static List<Block> reflectedBlocks(Object dropsHandler) {
        List<Block> blocks = new ArrayList<>();
        for (String method : List.of("getBlocks", "getBlockList", "getBrokenBlocks", "getAffectedBlocks")) {
            try {
                collectBlocks(invoke(dropsHandler, method), blocks);
                if (!blocks.isEmpty()) break;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                blocks.clear();
            }
        }
        Set<CoordinateKey> unique = new HashSet<>();
        List<Block> deduplicated = new ArrayList<>();
        for (Block block : blocks) {
            if (block != null && unique.add(key(block))) deduplicated.add(block);
        }
        return List.copyOf(deduplicated);
    }

    @SuppressWarnings("unchecked")
    private static void collectBlocks(Object value, List<Block> out) {
        if (value instanceof Block block) {
            out.add(block);
        } else if (value instanceof Collection<?> collection) {
            for (Object entry : collection) collectBlocks(entry, out);
        } else if (value instanceof Map<?, ?> map) {
            collectBlocks(map.keySet(), out);
            collectBlocks(map.values(), out);
        }
    }

    private static Block originalBlock(Object builder) throws ReflectiveOperationException {
        Object direct = invoke(builder, "getBlock");
        if (direct instanceof Block block) return block;
        Object originalEvent = invoke(builder, "getEvent");
        return originalEvent instanceof BlockBreakEvent breakEvent ? breakEvent.getBlock() : null;
    }

    public boolean connected() {
        return advancedEnchantments != null && advancedEnchantments.isEnabled();
    }

    public boolean bulkBridgeActive() {
        return bulkBridgeActive;
    }

    public String version() {
        return advancedEnchantments == null ? "unavailable" : advancedEnchantments.getDescription().getVersion();
    }

    public String source(Block block) {
        if (!plugin.config().snapshot().features().advancedEnchantments() || !connected()) return "BUKKIT";
        try {
            return block.hasMetadata("blockbreakevent-ignore") ? "ADVANCED_ENCHANTMENTS" : "BUKKIT";
        } catch (RuntimeException ignored) {
            return "BUKKIT";
        }
    }

    /** Records that RelicPrison already delivered the original block's rewards before AE ran. */
    public void markOriginalHandled(Block block) {
        long now = System.currentTimeMillis();
        handledOriginals.put(key(block), now);
        if (handledOriginals.size() > 4096) {
            handledOriginals.entrySet().removeIf(entry -> now - entry.getValue() > ORIGINAL_MARK_MILLIS);
        }
    }

    private boolean wasOriginalHandled(Block block) {
        long now = System.currentTimeMillis();
        Long marked = handledOriginals.remove(key(block));
        return marked != null && now - marked <= ORIGINAL_MARK_MILLIS;
    }

    /** Removes a synthetic AE block after RelicPrison has delivered its rewards. */
    public void consumeSyntheticBlock(Block block) {
        ignoreBlockEvent(block);
        block.setType(Material.AIR, false);
    }

    /** Marks a manually removed block so AE does not recursively process it. */
    public void ignoreBlockEvent(Block block) {
        if (!plugin.config().snapshot().features().advancedEnchantments()) return;
        Method method = setIgnoreBlockEvent;
        if (method == null) return;
        try {
            method.invoke(null, block);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            logBridgeError("AdvancedEnchantments setIgnoreBlockEvent failed", ex);
            setIgnoreBlockEvent = null;
        }
    }

    private void logBridgeError(String message, Throwable error) {
        long now = System.currentTimeMillis();
        long previous = lastBridgeErrorLog.get();
        if (now - previous < ERROR_LOG_INTERVAL_MILLIS || !lastBridgeErrorLog.compareAndSet(previous, now)) return;
        if (error == null) plugin.getLogger().warning(message);
        else plugin.getLogger().warning(message + ": " + rootMessage(error));
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        if (target == null) throw new NoSuchMethodException(name + " target is null");
        Method selected = null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            if (compatible(method.getParameterTypes(), arguments)) {
                selected = method;
                break;
            }
        }
        if (selected == null) throw new NoSuchMethodException(target.getClass().getName() + '#' + name);
        try {
            return selected.invoke(target, arguments);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw ex;
        }
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

    private static CoordinateKey key(Block block) {
        return new CoordinateKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record CoordinateKey(UUID world, int x, int y, int z) { }
}
