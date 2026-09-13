package site.mcrelicworld.relicprison.config;

import java.util.ArrayList;
import java.util.List;

public final class IntegrationAvailabilityValidator {
    private IntegrationAvailabilityValidator() {}

    public static List<String> validate(IntegrationConfig integrations, RuntimeState runtime) {
        List<String> failures = new ArrayList<>();
        if (integrations.vaultEnabled() && !runtime.vaultPluginPresent()) {
            failures.add("integrations.yml: vault.enabled requires Vault to be installed.");
        }
        if (integrations.vaultEnabled() && integrations.requireEconomyProvider()
                && !runtime.economyProviderPresent()) {
            failures.add("integrations.yml: vault.require-economy-provider requires a registered economy provider.");
        }
        if (integrations.luckPermsEnabled() && !runtime.luckPermsPresent()) {
            failures.add("integrations.yml: luckperms.enabled requires LuckPerms to be installed.");
        }
        if (integrations.itemsAdderEnabled() && !runtime.itemsAdderPresent()) {
            failures.add("integrations.yml: itemsadder.enabled requires the plugin to be installed.");
        }
        if (integrations.advancedEnchantmentsEnabled() && !runtime.advancedEnchantmentsPresent()) {
            failures.add("integrations.yml: advanced-enchantments.enabled requires the plugin to be installed.");
        }
        if (integrations.worldGuardEnabled() && !runtime.worldGuardPresent()) {
            failures.add("integrations.yml: worldguard.enabled requires WorldGuard to be installed.");
        }
        if (integrations.worldEditFaweEnabled() && !runtime.worldEditPresent() && !runtime.fawePresent()) {
            failures.add("integrations.yml: worldedit-fawe.enabled requires WorldEdit or FAWE to be installed.");
        }
        return List.copyOf(failures);
    }

    public record RuntimeState(
            boolean vaultPluginPresent,
            boolean economyProviderPresent,
            boolean luckPermsPresent,
            boolean worldGuardPresent,
            boolean worldEditPresent,
            boolean fawePresent,
            boolean itemsAdderPresent,
            boolean advancedEnchantmentsPresent
    ) {}
}
