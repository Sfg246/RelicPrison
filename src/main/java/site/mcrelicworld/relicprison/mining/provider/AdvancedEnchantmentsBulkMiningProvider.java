package site.mcrelicworld.relicprison.mining.provider;

import site.mcrelicworld.relicprison.integration.AdvancedEnchantmentsIntegration;

public final class AdvancedEnchantmentsBulkMiningProvider implements BulkMiningProvider {
    private static final ProviderCapabilities CONNECTED =
            new ProviderCapabilities(true, false, false, false);
    private static final ProviderCapabilities DISCONNECTED =
            new ProviderCapabilities(false, false, false, false);

    private final AdvancedEnchantmentsIntegration integration;

    public AdvancedEnchantmentsBulkMiningProvider(AdvancedEnchantmentsIntegration integration) {
        this.integration = integration;
    }

    @Override public String id() { return "advancedenchantments"; }

    @Override public ProviderCapabilities capabilities() {
        return integration != null && integration.connected() && integration.bulkBridgeActive()
                ? CONNECTED : DISCONNECTED;
    }
}
