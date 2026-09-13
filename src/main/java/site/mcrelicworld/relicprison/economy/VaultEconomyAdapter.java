package site.mcrelicworld.relicprison.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;

/**
 * Runtime Vault bridge. The service registration object is deliberately handled
 * as Object because Bukkit exposes RegisteredServiceProvider as a class. Keeping
 * this boundary reflective also prevents offline verification stubs from changing
 * the generated invoke opcode.
 */
public final class VaultEconomyAdapter {
    private final RelicPrisonPlugin plugin;
    private Object economy;
    private Method getBalance;
    private Method withdraw;
    private Method deposit;

    public VaultEconomyAdapter(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) {
                throw new IllegalStateException("Vault found no registered economy provider");
            }

            Object provider = resolveProvider(registration);
            if (provider == null) {
                throw new IllegalStateException("Vault found no registered economy provider");
            }

            economy = provider;
            getBalance = findPlayerMethod(economyClass, "getBalance", 1);
            withdraw = findPlayerMethod(economyClass, "withdrawPlayer", 2);
            deposit = findPlayerMethod(economyClass, "depositPlayer", 2);
            plugin.getLogger().info("Vault economy connected: " + economy.getClass().getName());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Vault economy API is unavailable or incompatible", ex);
        }
    }

    public boolean connected() {
        return economy != null;
    }

    public String providerName() {
        return economy == null ? "unavailable" : economy.getClass().getName();
    }

    public BigDecimal balance(Player player) {
        ensureReady();
        try {
            Object value = getBalance.invoke(economy, player);
            return BigDecimal.valueOf(((Number) value).doubleValue());
        } catch (ReflectiveOperationException ex) {
            throw new EconomyException("Unable to read balance", ex);
        }
    }

    public Transaction withdraw(Player player, BigDecimal amount) {
        return transact(withdraw, player, amount);
    }

    public Transaction deposit(Player player, BigDecimal amount) {
        return transact(deposit, player, amount);
    }

    private Transaction transact(Method method, Player player, BigDecimal amount) {
        ensureReady();
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Economy amount cannot be negative");
        }
        double value = amount.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Economy amount is too large");
        }
        try {
            Object response = method.invoke(economy, player, value);
            Method successMethod = response.getClass().getMethod("transactionSuccess");
            boolean success = Boolean.TRUE.equals(successMethod.invoke(response));
            String error = "";
            try {
                Field field = response.getClass().getField("errorMessage");
                Object raw = field.get(response);
                error = raw == null ? "" : String.valueOf(raw);
            } catch (NoSuchFieldException ignored) {
                try {
                    Method errorMethod = response.getClass().getMethod("errorMessage");
                    Object raw = errorMethod.invoke(response);
                    error = raw == null ? "" : String.valueOf(raw);
                } catch (NoSuchMethodException ignoredAgain) {
                    error = success ? "" : "Economy provider rejected the transaction";
                }
            }
            return new Transaction(success, error);
        } catch (ReflectiveOperationException ex) {
            throw new EconomyException("Economy transaction failed", ex);
        }
    }

    static Object resolveProvider(Object registration) throws ReflectiveOperationException {
        Method providerMethod = registration.getClass().getMethod("getProvider");
        return providerMethod.invoke(registration);
    }

    private static Method findPlayerMethod(Class<?> economyClass, String name, int parameters) {
        for (Method method : economyClass.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != parameters) {
                continue;
            }
            Class<?> first = method.getParameterTypes()[0];
            if (first.getName().equals("org.bukkit.OfflinePlayer")
                    || first.getName().equals("org.bukkit.entity.Player")) {
                return method;
            }
        }
        throw new IllegalStateException("Vault economy method is missing: " + name);
    }

    private void ensureReady() {
        if (economy == null) {
            throw new IllegalStateException("Vault economy has not been initialized");
        }
    }

    public record Transaction(boolean success, String error) { }

    public static final class EconomyException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EconomyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
