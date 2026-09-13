package site.mcrelicworld.relicprison.mining.provider;

public record ProviderCapabilities(
        boolean bulkMining,
        boolean customBlocks,
        boolean customItems,
        boolean durableOperationIds
) { }
