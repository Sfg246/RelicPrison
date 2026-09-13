package site.mcrelicworld.relicprison.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IntegrationAvailabilityValidatorTest {
    @Test
    void disabledOptionalIntegrationsDoNotFailWhenPluginsAreMissing() {
        IntegrationConfig integrations = integrations(true, true, true, false, false, false, false);
        List<String> failures = IntegrationAvailabilityValidator.validate(integrations,
                new IntegrationAvailabilityValidator.RuntimeState(true, true, true,
                        false, false, false, false, false));
        assertEquals(List.of(), failures);
    }

    @Test
    void enabledOptionalIntegrationsReportOnlyTheirMissingAdapters() {
        IntegrationConfig integrations = integrations(true, true, true, false, false, true, true);
        List<String> failures = IntegrationAvailabilityValidator.validate(integrations,
                new IntegrationAvailabilityValidator.RuntimeState(true, true, true,
                        false, false, false, false, false));
        assertEquals(List.of(
                "integrations.yml: itemsadder.enabled requires the plugin to be installed.",
                "integrations.yml: advanced-enchantments.enabled requires the plugin to be installed."
        ), failures);
    }

    @Test
    void requiredIntegrationAbsenceIsRejected() {
        IntegrationConfig integrations = integrations(true, true, true, false, false, false, false);
        List<String> failures = IntegrationAvailabilityValidator.validate(integrations,
                new IntegrationAvailabilityValidator.RuntimeState(false, false, false,
                        false, false, false, false, false));
        assertTrue(failures.contains("integrations.yml: vault.enabled requires Vault to be installed."));
        assertTrue(failures.contains("integrations.yml: vault.require-economy-provider requires a registered economy provider."));
        assertTrue(failures.contains("integrations.yml: luckperms.enabled requires LuckPerms to be installed."));
        assertEquals(3, failures.size());
    }

    @Test
    void economyProviderAbsenceIsRejectedWhenVaultIsPresent() {
        IntegrationConfig integrations = integrations(true, true, true, false, false, false, false);
        List<String> failures = IntegrationAvailabilityValidator.validate(integrations,
                new IntegrationAvailabilityValidator.RuntimeState(true, false, true,
                        false, false, false, false, false));
        assertEquals(List.of("integrations.yml: vault.require-economy-provider requires a registered economy provider."),
                failures);
    }

    private static IntegrationConfig integrations(boolean vault, boolean economy, boolean luckPerms,
                                                  boolean worldGuard, boolean worldEditFawe,
                                                  boolean itemsAdder, boolean advancedEnchantments) {
        return new IntegrationConfig(vault, economy, luckPerms, worldGuard, worldEditFawe,
                itemsAdder, advancedEnchantments, true, true, "none",
                false, "relicprison.bypass.combat-teleport", "&cDenied.",
                new WorldGuardConfig("relicmine_", true, true, true, true, true, true, true, true));
    }
}
