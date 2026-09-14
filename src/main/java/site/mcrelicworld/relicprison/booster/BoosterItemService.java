package site.mcrelicworld.relicprison.booster;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.util.ColorUtil;
import site.mcrelicworld.relicprison.util.DurationParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BoosterItemService {
    private final BoosterConfigRepository configs;
    private final NamespacedKey typeKey;
    private final NamespacedKey multiplierKey;
    private final NamespacedKey durationKey;
    private final NamespacedKey versionKey;
    private final NamespacedKey unsellableKey;

    public BoosterItemService(RelicPrisonPlugin plugin, BoosterConfigRepository configs) {
        this.configs = configs;
        this.typeKey = new NamespacedKey(plugin, "booster_type");
        this.multiplierKey = new NamespacedKey(plugin, "booster_multiplier");
        this.durationKey = new NamespacedKey(plugin, "booster_duration_ms");
        this.versionKey = new NamespacedKey(plugin, "booster_item_version");
        this.unsellableKey = new NamespacedKey(plugin, "unsellable");
    }

    public ItemStack create(boolean serverWide, BigDecimal multiplier, long durationMillis) {
        BoosterConfig config = configs.config();
        validate(multiplier, durationMillis);
        ItemStack item = new ItemStack(config.itemMaterial());
        ItemMeta meta = item.getItemMeta();
        String type = serverWide ? "SERVER" : "PERSONAL";
        meta.displayName(ColorUtil.component(replace(config.itemName(), multiplier, durationMillis, type)));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : config.itemLore()) lore.add(ColorUtil.component(replace(line, multiplier, durationMillis, type)));
        meta.lore(lore);
        if (config.customModelData() > 0) {
            var customModelData = meta.getCustomModelDataComponent();
            customModelData.setFloats(List.of((float) config.customModelData()));
            meta.setCustomModelDataComponent(customModelData);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, type);
        pdc.set(multiplierKey, PersistentDataType.STRING, multiplier.stripTrailingZeros().toPlainString());
        pdc.set(durationKey, PersistentDataType.LONG, durationMillis);
        pdc.set(versionKey, PersistentDataType.INTEGER, 1);
        pdc.set(unsellableKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public Optional<BoosterItem> read(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String type = pdc.get(typeKey, PersistentDataType.STRING);
        String rawMultiplier = pdc.get(multiplierKey, PersistentDataType.STRING);
        Long duration = pdc.get(durationKey, PersistentDataType.LONG);
        Integer version = pdc.get(versionKey, PersistentDataType.INTEGER);
        if (type == null || rawMultiplier == null || duration == null || version == null || version != 1) return Optional.empty();
        try {
            BigDecimal multiplier = new BigDecimal(rawMultiplier);
            boolean serverWide = switch (type.toUpperCase(Locale.ROOT)) {
                case "PERSONAL" -> false;
                case "SERVER" -> true;
                default -> throw new IllegalArgumentException("Unknown booster type");
            };
            validate(multiplier, duration);
            return Optional.of(new BoosterItem(serverWide, multiplier, duration));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public boolean isProtected(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(unsellableKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private void validate(BigDecimal multiplier, long durationMillis) {
        BoosterConfig config = configs.config();
        if (multiplier == null || multiplier.signum() <= 0 || multiplier.compareTo(config.maximumBoosterMultiplier()) > 0) {
            throw new IllegalArgumentException("Booster multiplier is outside configured limits");
        }
        if (durationMillis <= 0 || durationMillis > config.maximumDuration().toMillis()) {
            throw new IllegalArgumentException("Booster duration is outside configured limits");
        }
    }

    private static String replace(String text, BigDecimal multiplier, long duration, String type) {
        return text.replace("{multiplier}", multiplier.stripTrailingZeros().toPlainString())
                .replace("{duration}", DurationParser.format(duration))
                .replace("{type}", type.toLowerCase(Locale.ROOT));
    }

    public record BoosterItem(boolean serverWide, BigDecimal multiplier, long durationMillis) {}
}
