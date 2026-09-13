package site.mcrelicworld.relicprison.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Runtime adapter verified against ItemsAdder 4.0.16 without linking RelicPrison to obfuscated internals. */
public final class ItemsAdderIntegration {
    private final RelicPrisonPlugin plugin;
    private boolean connected;
    private String version = "unavailable";
    private Class<?> customStackClass;
    private Class<?> customBlockClass;
    private Method stackByItem;
    private Method stackGetInstance;
    private Method stackGetItem;
    private Method stackGetNamespacedId;
    private Method blockByPlaced;
    private Method blockGetLoot;
    private Method blockPlace;
    private Method blockRemove;
    private Method blockIsInRegistry;

    public ItemsAdderIntegration(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    public void initialize() {
        Plugin dependency = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (dependency == null || !dependency.isEnabled()) {
            connected = false;
            if (plugin.config().snapshot().features().itemsAdder()) {
                plugin.getLogger().warning("ItemsAdder support is enabled but ItemsAdder is not loaded.");
            }
            return;
        }
        try {
            customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack", true, dependency.getClass().getClassLoader());
            customBlockClass = Class.forName("dev.lone.itemsadder.api.CustomBlock", true, dependency.getClass().getClassLoader());
            stackByItem = customStackClass.getMethod("byItemStack", ItemStack.class);
            stackGetInstance = customStackClass.getMethod("getInstance", String.class);
            stackGetItem = customStackClass.getMethod("getItemStack");
            stackGetNamespacedId = customStackClass.getMethod("getNamespacedID");
            blockByPlaced = customBlockClass.getMethod("byAlreadyPlaced", Block.class);
            blockGetLoot = customBlockClass.getMethod("getLoot", Block.class, ItemStack.class, boolean.class);
            blockPlace = customBlockClass.getMethod("place", String.class, Location.class);
            blockRemove = customBlockClass.getMethod("remove", Location.class);
            blockIsInRegistry = customBlockClass.getMethod("isInRegistry", String.class);
            version = dependency.getDescription().getVersion();
            connected = true;
            plugin.getLogger().info("ItemsAdder connected: runtime " + version + "; RelicPrison verified against 4.0.16.");
        } catch (ReflectiveOperationException | LinkageError error) {
            connected = false;
            plugin.getLogger().warning("ItemsAdder integration could not initialize: " + rootMessage(error));
        }
    }

    public boolean connected() { return connected; }
    public boolean enabled() { return connected && plugin.config().snapshot().features().itemsAdder(); }
    public String version() { return version; }

    public Optional<String> customItemId(ItemStack item) {
        if (!connected || item == null || item.getType().isAir()) return Optional.empty();
        try {
            Object custom = stackByItem.invoke(null, item);
            return custom == null ? Optional.empty() : Optional.of(normalize(String.valueOf(stackGetNamespacedId.invoke(custom))));
        } catch (ReflectiveOperationException | LinkageError error) {
            warnOnce("item lookup", error);
            return Optional.empty();
        }
    }

    public Optional<String> customBlockId(Block block) {
        if (!connected || block == null) return Optional.empty();
        try {
            Object custom = blockByPlaced.invoke(null, block);
            return custom == null ? Optional.empty() : Optional.of(normalize(String.valueOf(stackGetNamespacedId.invoke(custom))));
        } catch (ReflectiveOperationException | LinkageError error) {
            warnOnce("block lookup", error);
            return Optional.empty();
        }
    }

    public List<ItemStack> blockLoot(Block block, ItemStack tool) {
        if (!connected) return List.of();
        try {
            Object raw = blockGetLoot.invoke(null, block, tool, false);
            if (!(raw instanceof Collection<?> values)) return List.of();
            List<ItemStack> result = new ArrayList<>();
            for (Object value : values) if (value instanceof ItemStack item && item.getAmount() > 0) result.add(item.clone());
            return List.copyOf(result);
        } catch (ReflectiveOperationException | LinkageError error) {
            warnOnce("custom block loot", error);
            return List.of();
        }
    }

    public Optional<ItemStack> item(String namespacedId, int amount) {
        if (!connected) return Optional.empty();
        try {
            Object custom = stackGetInstance.invoke(null, normalize(namespacedId));
            if (custom == null) return Optional.empty();
            ItemStack item = ((ItemStack) stackGetItem.invoke(custom)).clone();
            item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
            return Optional.of(item);
        } catch (ReflectiveOperationException | LinkageError error) {
            warnOnce("custom item creation", error);
            return Optional.empty();
        }
    }

    public boolean isRegisteredBlock(String namespacedId) {
        if (!connected) return false;
        try { return Boolean.TRUE.equals(blockIsInRegistry.invoke(null, normalize(namespacedId))); }
        catch (ReflectiveOperationException | LinkageError error) { return false; }
    }

    public boolean placeBlock(String namespacedId, Location location) {
        if (!enabled()) return false;
        try {
            removeBlock(location);
            return blockPlace.invoke(null, normalize(namespacedId), location) != null;
        } catch (ReflectiveOperationException | LinkageError error) {
            warnOnce("custom block placement", error);
            return false;
        }
    }

    public boolean removeBlock(Location location) {
        if (!connected) return false;
        try { return Boolean.TRUE.equals(blockRemove.invoke(null, location)); }
        catch (ReflectiveOperationException | LinkageError error) { return false; }
    }

    private boolean warned;
    private void warnOnce(String operation, Throwable error) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("ItemsAdder " + operation + " failed; integration remains fail-safe: " + rootMessage(error));
    }

    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }
}
