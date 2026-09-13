package site.mcrelicworld.relicprison.reset;

import site.mcrelicworld.relicprison.api.model.MineResetState;

import java.util.concurrent.atomic.AtomicLong;

public final class MineRuntime {
    private final String mineId;
    private final AtomicLong remainingBlocks;
    private final AtomicLong resetCount;
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong savedRevision = new AtomicLong();
    private volatile long lastReset;
    private volatile long nextReset;
    private volatile long lastResetDurationMillis;
    private volatile long lastRecountAt;
    private volatile String lastRecountReason = "";
    private volatile String lastRecountActor = "";
    private volatile MineResetState.State state;

    public MineRuntime(String mineId, long remainingBlocks, long resetCount, long lastReset,
                       long nextReset, long lastResetDurationMillis, MineResetState.State state) {
        this(mineId, remainingBlocks, resetCount, lastReset, nextReset, lastResetDurationMillis, state, 0, "", "");
    }

    public MineRuntime(String mineId, long remainingBlocks, long resetCount, long lastReset,
                       long nextReset, long lastResetDurationMillis, MineResetState.State state,
                       long lastRecountAt, String lastRecountReason, String lastRecountActor) {
        this.mineId = mineId;
        this.remainingBlocks = new AtomicLong(Math.max(0, remainingBlocks));
        this.resetCount = new AtomicLong(Math.max(0, resetCount));
        this.lastReset = Math.max(0, lastReset);
        this.nextReset = Math.max(0, nextReset);
        this.lastResetDurationMillis = Math.max(0, lastResetDurationMillis);
        this.lastRecountAt = Math.max(0, lastRecountAt);
        this.lastRecountReason = lastRecountReason == null ? "" : lastRecountReason;
        this.lastRecountActor = lastRecountActor == null ? "" : lastRecountActor;
        this.state = state == null ? MineResetState.State.IDLE : state;
    }

    public String mineId() { return mineId; }
    public long remainingBlocks() { return remainingBlocks.get(); }
    public long resetCount() { return resetCount.get(); }
    public long lastReset() { return lastReset; }
    public long nextReset() { return nextReset; }
    public long lastResetDurationMillis() { return lastResetDurationMillis; }
    public long lastRecountAt() { return lastRecountAt; }
    public String lastRecountReason() { return lastRecountReason; }
    public String lastRecountActor() { return lastRecountActor; }
    public MineResetState.State state() { return state; }

    public long recordBroken(long amount) {
        if (amount <= 0) return remainingBlocks.get();
        long updated = remainingBlocks.updateAndGet(value -> Math.max(0, value - amount));
        markDirty();
        return updated;
    }

    public void state(MineResetState.State value) {
        state = value;
        markDirty();
    }

    public void scheduleNext(long epochMillis) {
        nextReset = Math.max(0, epochMillis);
        markDirty();
    }

    public void resetCompleted(long totalBlocks, long completedAt, long durationMillis, long nextResetAt) {
        remainingBlocks.set(Math.max(0, totalBlocks));
        resetCount.incrementAndGet();
        lastReset = completedAt;
        lastResetDurationMillis = Math.max(0, durationMillis);
        nextReset = Math.max(0, nextResetAt);
        state = MineResetState.State.IDLE;
        markDirty();
    }

    public void resetFailed() {
        state = MineResetState.State.FAILED;
        markDirty();
    }

    public void reconcileVolume(long totalBlocks) {
        long normalized = Math.max(0, totalBlocks);
        while (true) {
            long current = remainingBlocks.get();
            long replacement = current <= 0 || current > normalized ? normalized : current;
            if (replacement == current) return;
            if (remainingBlocks.compareAndSet(current, replacement)) {
                markDirty();
                return;
            }
        }
    }

    public void repairRemaining(long remaining, String reason, String actor) {
        remainingBlocks.set(Math.max(0, remaining));
        lastRecountAt = System.currentTimeMillis();
        lastRecountReason = reason == null ? "" : reason;
        lastRecountActor = actor == null ? "" : actor;
        markDirty();
    }

    public double minedPercentage(long totalBlocks) {
        if (totalBlocks <= 0) return 0;
        return Math.max(0, Math.min(100, (totalBlocks - remainingBlocks()) * 100.0 / totalBlocks));
    }

    public long revision() { return revision.get(); }
    public boolean dirty() { return revision.get() != savedRevision.get(); }
    public void markDirty() { revision.incrementAndGet(); }
    public void markSaved(long value) { savedRevision.accumulateAndGet(value, Math::max); }
}
