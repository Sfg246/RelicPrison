package site.mcrelicworld.relicprison.mine;

public enum StructureOperationStage {
    PREPARED,
    TARGET_REGISTERED,
    COPYING,
    DESTINATION_COPIED,
    TARGET_ACTIVATED,
    CLEARING_SOURCE,
    COMPLETED,
    ROLLING_BACK,
    ROLLED_BACK,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == ROLLED_BACK;
    }
}
