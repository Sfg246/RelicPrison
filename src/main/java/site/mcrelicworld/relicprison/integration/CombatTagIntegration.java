package site.mcrelicworld.relicprison.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.config.IntegrationConfig;
import site.mcrelicworld.relicprison.logging.LogCategory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CombatTagIntegration {
    private static final List<String> AUTO_PROVIDERS = List.of("CombatLogX", "DeluxeCombat", "PvPManager", "CombatTagPlus");

    private final RelicPrisonPlugin plugin;
    private Plugin dependency;
    private boolean detected;
    private String provider = "none";
    private boolean warnedUnsupported;

    public CombatTagIntegration(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        IntegrationConfig config = plugin.config().snapshot().integrations();
        warnedUnsupported = false;
        provider = normalize(config.combatProvider());
        dependency = null;
        detected = false;
        if (!config.combatTeleportRestrictionEnabled() || provider.equals("none")) {
            plugin.structuredLogger().debug(LogCategory.INTEGRATION, "Combat-tag mine teleport restriction disabled.");
            return;
        }
        dependency = resolveProvider(provider);
        detected = dependency != null;
        if (dependency == null || !dependency.isEnabled()) {
            plugin.structuredLogger().warning(LogCategory.INTEGRATION,
                    "Combat-tag provider " + provider + " is configured but unavailable; mine teleport restriction remains inactive.");
            dependency = null;
            return;
        }
        plugin.structuredLogger().info(LogCategory.INTEGRATION,
                "Combat-tag provider connected: " + dependency.getName() + " " + dependency.getPluginMeta().getVersion() + ".");
    }

    public boolean blocksMineTeleport(Player player) {
        IntegrationConfig config = plugin.config().snapshot().integrations();
        if (!config.combatTeleportRestrictionEnabled() || player == null || dependency == null) return false;
        if (player.hasPermission(config.combatTeleportBypassPermission())) return false;
        Boolean direct = callBoolean(dependency, player);
        if (direct != null) return direct;
        for (String accessor : List.of("getCombatManager", "getTagManager", "getUserManager", "getAPI", "getApi")) {
            Object manager = callObject(dependency, accessor);
            if (manager == null) continue;
            Boolean result = callBoolean(manager, player);
            if (result != null) return result;
        }
        warnUnsupported();
        return false;
    }

    public boolean connected() {
        return dependency != null && dependency.isEnabled();
    }

    public boolean detected() {
        return detected;
    }

    public String provider() {
        return provider;
    }

    public String version() {
        return dependency == null ? "unavailable" : dependency.getPluginMeta().getVersion();
    }

    private Plugin resolveProvider(String configured) {
        if (configured.equals("auto")) {
            for (String name : AUTO_PROVIDERS) {
                Plugin candidate = Bukkit.getPluginManager().getPlugin(name);
                if (candidate != null) return candidate;
            }
            return null;
        }
        Plugin direct = Bukkit.getPluginManager().getPlugin(configured);
        if (direct != null) return direct;
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (normalize(candidate.getName()).equals(configured)) return candidate;
        }
        return null;
    }

    private Boolean callBoolean(Object target, Player player) {
        for (String methodName : List.of("isInCombat", "isTagged", "isCombatTagged", "inCombat", "isPlayerTagged")) {
            Boolean value = invokeBoolean(target, methodName, Player.class, player);
            if (value != null) return value;
            value = invokeBoolean(target, methodName, UUID.class, player.getUniqueId());
            if (value != null) return value;
            value = invokeBoolean(target, methodName, String.class, player.getName());
            if (value != null) return value;
        }
        return null;
    }

    private Boolean invokeBoolean(Object target, String name, Class<?> parameter, Object value) {
        try {
            Method method = target.getClass().getMethod(name, parameter);
            Object result = method.invoke(target, value);
            return result instanceof Boolean bool ? bool : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private Object callObject(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private void warnUnsupported() {
        if (warnedUnsupported) return;
        warnedUnsupported = true;
        plugin.structuredLogger().warning(LogCategory.INTEGRATION,
                "Combat-tag provider " + dependency.getName()
                        + " does not expose a supported combat lookup method; mine teleport restriction remains fail-open.");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "none" : value.trim().toLowerCase(Locale.ROOT);
    }
}
