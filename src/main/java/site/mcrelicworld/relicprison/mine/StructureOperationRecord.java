package site.mcrelicworld.relicprison.mine;

import java.util.Objects;
import java.util.UUID;

public record StructureOperationRecord(
        UUID id,
        StructureOperationType type,
        StructureOperationStage stage,
        UUID staffId,
        MineDefinition source,
        MineDefinition target,
        long createdAt,
        long updatedAt,
        long copiedBlocks,
        long clearedBlocks,
        String provider,
        String failure
) {
    public StructureOperationRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(staffId, "staffId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        provider = provider == null || provider.isBlank() ? "PENDING" : provider;
        failure = failure == null || failure.isBlank() ? null : failure;
    }

    public StructureOperationRecord withStage(StructureOperationStage next) {
        return new StructureOperationRecord(id, type, next, staffId, source, target, createdAt,
                System.currentTimeMillis(), copiedBlocks, clearedBlocks, provider, failure);
    }

    public StructureOperationRecord withProgress(long copied, long cleared, String nextProvider) {
        return new StructureOperationRecord(id, type, stage, staffId, source, target, createdAt,
                System.currentTimeMillis(), copied, cleared, nextProvider, failure);
    }

    public StructureOperationRecord withFailure(Throwable error) {
        String message = error == null ? null : rootMessage(error);
        return new StructureOperationRecord(id, type, stage, staffId, source, target,
                createdAt, System.currentTimeMillis(), copiedBlocks, clearedBlocks, provider, message);
    }

    public StructureOperationRecord withFailureMessage(String message) {
        return new StructureOperationRecord(id, type, stage, staffId, source, target,
                createdAt, System.currentTimeMillis(), copiedBlocks, clearedBlocks, provider, message);
    }

    public boolean recoverable() {
        return !stage.terminal();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
