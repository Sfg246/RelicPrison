package site.mcrelicworld.relicprison.testsupport;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.CreativeCategory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class PaperTestRegistryAccess implements RegistryAccess {
    // Paper still requires this legacy bridge on RegistryAccess; tests use RegistryKey lookups.
    @SuppressWarnings({"deprecation", "removal"})
    @Override public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return registry(type);
    }

    @Override public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
        if (registryKey == RegistryKey.BLOCK) return registry(RegistryKind.BLOCK);
        if (registryKey == RegistryKey.ITEM) return registry(RegistryKind.ITEM);
        return registry(RegistryKind.GENERIC);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Keyed> Registry<T> registry(Class<T> type) {
        if (type == BlockType.class || nameOf(type).contains("BlockType")) return registry(RegistryKind.BLOCK);
        if (type == ItemType.class || nameOf(type).contains("ItemType")) return registry(RegistryKind.ITEM);
        return registry(RegistryKind.GENERIC);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Keyed> Registry<T> registry(RegistryKind registryKind) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("get") || name.equals("match")) {
                Material material = material(args[0]);
                if (registryKind == RegistryKind.BLOCK) return blockType(material);
                if (registryKind == RegistryKind.ITEM) return itemType(material);
                return genericKeyed(material);
            }
            if (name.equals("getKey")) return ((Keyed) args[0]).getKey();
            if (name.equals("iterator")) return Collections.emptyIterator();
            if (name.equals("stream")) return Stream.empty();
            if (name.equals("keyStream")) return Stream.empty();
            if (name.equals("size")) return 0;
            if (name.equals("hasTag")) return false;
            if (name.equals("getTag")) return null;
            if (name.equals("getTags")) return Set.of();
            if (name.equals("toString")) return "PaperTestRegistry";
            return defaultValue(method.getReturnType());
        };
        return (Registry<T>) Proxy.newProxyInstance(PaperTestRegistryAccess.class.getClassLoader(),
                new Class<?>[]{Registry.class}, handler);
    }

    private enum RegistryKind {
        BLOCK,
        ITEM,
        GENERIC
    }

    private static String nameOf(Class<?> type) {
        return type == null ? "" : type.getName();
    }

    private static Material material(Object key) {
        String raw = key instanceof NamespacedKey namespacedKey ? namespacedKey.getKey()
                : key instanceof Key adventureKey ? adventureKey.value()
                : String.valueOf(key);
        int separator = raw.indexOf(':');
        if (separator >= 0) raw = raw.substring(separator + 1);
        Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return material == null ? Material.STONE : material;
    }

    private static Keyed genericKeyed(Material material) {
        return () -> material.getKey();
    }

    private static ItemType itemType(Material material) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("createItemStack")) {
                int amount = args == null || args.length == 0 ? 1 : (Integer) args[0];
                return new SimpleItemStack(material, amount);
            }
            if (name.equals("asMaterial")) return material;
            if (name.equals("getKey")) return material.getKey();
            if (name.equals("key")) return material.getKey();
            if (name.equals("translationKey") || name.equals("getTranslationKey")) return "item.minecraft." + material.name().toLowerCase(Locale.ROOT);
            if (name.equals("getMaxStackSize")) return material.getMaxStackSize();
            if (name.equals("getMaxDurability")) return (short) 0;
            if (name.equals("hasBlockType")) return true;
            if (name.equals("getBlockType")) return blockType(material);
            if (name.equals("getItemMetaClass")) return ItemMeta.class;
            if (name.equals("getDefaultAttributeModifiers")) return com.google.common.collect.ImmutableMultimap.of();
            if (name.equals("getDefaultDataTypes")) return Set.<DataComponentType>of();
            if (name.equals("isEnabledByFeature")) return true;
            if (name.equals("toString")) return "TestItemType[" + material + "]";
            return defaultValue(method.getReturnType());
        };
        return (ItemType) Proxy.newProxyInstance(PaperTestRegistryAccess.class.getClassLoader(),
                new Class<?>[]{ItemType.class}, handler);
    }

    private static BlockType blockType(Material material) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("asMaterial")) return material;
            if (name.equals("getKey")) return material.getKey();
            if (name.equals("key")) return material.getKey();
            if (name.equals("translationKey") || name.equals("getTranslationKey")) return "block.minecraft." + material.name().toLowerCase(Locale.ROOT);
            if (name.equals("isAir")) return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
            if (name.equals("hasItemType")) return true;
            if (name.equals("getItemType")) return itemType(material);
            if (name.equals("getBlockDataClass")) return BlockData.class;
            if (name.equals("createBlockDataStates")) return List.of();
            if (name.equals("isEnabledByFeature")) return true;
            if (name.equals("toString")) return "TestBlockType[" + material + "]";
            return defaultValue(method.getReturnType());
        };
        return (BlockType) Proxy.newProxyInstance(PaperTestRegistryAccess.class.getClassLoader(),
                new Class<?>[]{BlockType.class}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        if (type == Iterator.class) return Collections.emptyIterator();
        if (type == Stream.class) return Stream.empty();
        if (type == Set.class) return Set.of();
        if (type == Collection.class) return Set.of();
        if (type == CreativeCategory.class) return null;
        if (type == ItemRarity.class) return null;
        if (type == ItemType.class) return null;
        if (type == BlockType.class) return null;
        if (type == Tag.class || type == TagKey.class || type == World.class || type == EquipmentSlot.class) return null;
        return null;
    }

    private static final class SimpleItemStack extends ItemStack {
        private Material type;
        private int amount;

        private SimpleItemStack(Material type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        @Override public Material getType() { return type; }
        // Required by the mutable ItemStack test double on the pinned Paper API.
        @SuppressWarnings("deprecation")
        @Override public void setType(Material type) { this.type = type; }
        @Override public ItemStack withType(Material type) { return new SimpleItemStack(type, amount); }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return type.getMaxStackSize(); }
        @Override public boolean hasItemMeta() { return false; }
        @Override public boolean isSimilar(ItemStack stack) { return stack != null && stack.getType() == type; }
        @Override public SimpleItemStack clone() { return new SimpleItemStack(type, amount); }
    }
}
