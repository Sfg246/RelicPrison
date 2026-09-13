package site.mcrelicworld.relicprison.mining;

import java.util.List;
import java.util.UUID;

public record BulkMiningTransactionRecord(
        String transactionId,
        UUID playerId,
        String source,
        String mineId,
        BulkMiningTransactionState state,
        int blockCount,
        int consumedCount,
        String rewardPackageId,
        List<BulkBlockSnapshot> blockSnapshots,
        String rewardPayload,
        String commitPayload,
        List<UUID> spawnedEntityIds,
        long createdAt,
        long updatedAt,
        int recoveryAttempts,
        String failureSummary
) {
    public BulkMiningTransactionRecord {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }
        if (playerId == null) throw new IllegalArgumentException("playerId is required");
        source = source == null || source.isBlank() ? "UNKNOWN" : source;
        mineId = mineId == null || mineId.isBlank() ? "unknown" : mineId;
        rewardPackageId = rewardPackageId == null ? "" : rewardPackageId;
        blockSnapshots = List.copyOf(blockSnapshots == null ? List.of() : blockSnapshots);
        rewardPayload = rewardPayload == null ? "" : rewardPayload;
        commitPayload = commitPayload == null ? "" : commitPayload;
        spawnedEntityIds = List.copyOf(spawnedEntityIds == null ? List.of() : spawnedEntityIds);
        failureSummary = failureSummary == null ? "" : failureSummary;
    }

    public BulkMiningTransactionRecord(String transactionId, UUID playerId, String source, String mineId,
                                       BulkMiningTransactionState state, int blockCount, int consumedCount,
                                       String rewardPackageId, List<BulkBlockSnapshot> blockSnapshots,
                                       String rewardPayload, long createdAt, long updatedAt, int recoveryAttempts,
                                       String failureSummary) {
        this(transactionId, playerId, source, mineId, state, blockCount, consumedCount, rewardPackageId,
                blockSnapshots, rewardPayload, "", List.of(), createdAt, updatedAt, recoveryAttempts, failureSummary);
    }

    public BulkMiningTransactionRecord(String transactionId, UUID playerId, String source, String mineId,
                                       BulkMiningTransactionState state, int blockCount, int consumedCount,
                                       String rewardPackageId, List<BulkBlockSnapshot> blockSnapshots,
                                       String rewardPayload, String commitPayload, long createdAt, long updatedAt,
                                       int recoveryAttempts, String failureSummary) {
        this(transactionId, playerId, source, mineId, state, blockCount, consumedCount, rewardPackageId,
                blockSnapshots, rewardPayload, commitPayload, List.of(), createdAt, updatedAt, recoveryAttempts,
                failureSummary);
    }
}
