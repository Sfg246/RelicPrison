package site.mcrelicworld.relicprison.api.model;

public record MineResetState(String mineId, State state, long processedBlocks, long totalBlocks) {
    public enum State { IDLE, WARNING, QUEUED, PREPARING, RESETTING, COMPLETING, FAILED }
}
