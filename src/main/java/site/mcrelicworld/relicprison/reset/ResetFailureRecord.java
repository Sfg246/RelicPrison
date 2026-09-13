package site.mcrelicworld.relicprison.reset;

public record ResetFailureRecord(
        String resetId,
        String mineId,
        String reason,
        String stage,
        long startedAt,
        long failedAt,
        long durationMillis,
        long totalBlocks,
        long processedBlocks,
        long vanillaBlocks,
        long itemsAdderBlocks,
        long airBlocks,
        long resetCountAtFailure,
        String errorSummary,
        boolean retryEligible
) {}
