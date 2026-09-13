package site.mcrelicworld.relicprison.database;

import site.mcrelicworld.relicprison.api.model.PlayerProfileView;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerProfile implements PlayerProfileView {
    private static final int DATA_VERSION = 1;

    private final UUID uuid;
    private volatile String lastName;
    private volatile String currentRank;
    private volatile String currentPrestige;
    private final long firstJoin;
    private volatile long lastJoin;
    private volatile boolean autoSell;
    private volatile boolean autoPickup;
    private volatile boolean autoSmelt;
    private volatile boolean autoBlock;
    private final AtomicLong lifetimeBlocks;
    private final AtomicLong dailyBlocks;
    private final AtomicLong weeklyBlocks;
    private final AtomicLong monthlyBlocks;
    private volatile String dailyPeriod;
    private volatile String weeklyPeriod;
    private volatile String monthlyPeriod;
    private volatile BigDecimal moneyEarned;
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong savedRevision = new AtomicLong();

    public PlayerProfile(UUID uuid, String lastName, String currentRank, String currentPrestige,
                         long firstJoin, long lastJoin, boolean autoSell, boolean autoPickup,
                         boolean autoSmelt, boolean autoBlock, long lifetimeBlocks, long dailyBlocks,
                         long weeklyBlocks, long monthlyBlocks, String dailyPeriod, String weeklyPeriod,
                         String monthlyPeriod, BigDecimal moneyEarned) {
        this.uuid = Objects.requireNonNull(uuid);
        this.lastName = Objects.requireNonNullElse(lastName, "unknown");
        this.currentRank = Objects.requireNonNullElse(currentRank, "a");
        this.currentPrestige = currentPrestige;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
        this.autoSell = autoSell;
        this.autoPickup = autoPickup;
        this.autoSmelt = autoSmelt;
        this.autoBlock = autoBlock;
        this.lifetimeBlocks = new AtomicLong(lifetimeBlocks);
        this.dailyBlocks = new AtomicLong(dailyBlocks);
        this.weeklyBlocks = new AtomicLong(weeklyBlocks);
        this.monthlyBlocks = new AtomicLong(monthlyBlocks);
        this.dailyPeriod = Objects.requireNonNullElse(dailyPeriod, "unknown");
        this.weeklyPeriod = Objects.requireNonNullElse(weeklyPeriod, "unknown");
        this.monthlyPeriod = Objects.requireNonNullElse(monthlyPeriod, "unknown");
        this.moneyEarned = moneyEarned == null ? BigDecimal.ZERO : moneyEarned;
    }

    public static PlayerProfile create(UUID uuid, String name, String startingRank, long now, PeriodKeys periods) {
        PlayerProfile profile = new PlayerProfile(uuid, name, Objects.requireNonNull(startingRank), null, now, now,
                false, true, true, true, 0, 0, 0, 0,
                periods.daily(), periods.weekly(), periods.monthly(), BigDecimal.ZERO);
        profile.markDirty();
        return profile;
    }

    @Override public UUID uuid() { return uuid; }
    @Override public String lastName() { return lastName; }
    @Override public String currentRank() { return currentRank; }
    @Override public String currentPrestige() { return currentPrestige; }
    @Override public long firstJoin() { return firstJoin; }
    @Override public long lastJoin() { return lastJoin; }
    @Override public boolean autoSell() { return autoSell; }
    @Override public boolean autoPickup() { return autoPickup; }
    @Override public boolean autoSmelt() { return autoSmelt; }
    @Override public boolean autoBlock() { return autoBlock; }
    @Override public long lifetimeBlocks() { return lifetimeBlocks.get(); }
    @Override public long dailyBlocks() { return dailyBlocks.get(); }
    @Override public long weeklyBlocks() { return weeklyBlocks.get(); }
    @Override public long monthlyBlocks() { return monthlyBlocks.get(); }
    @Override public String dailyPeriod() { return dailyPeriod; }
    @Override public String weeklyPeriod() { return weeklyPeriod; }
    @Override public String monthlyPeriod() { return monthlyPeriod; }
    @Override public BigDecimal moneyEarned() { return moneyEarned; }
    @Override public int dataVersion() { return DATA_VERSION; }

    public void touch(String name, long now) { lastName = Objects.requireNonNullElse(name, lastName); lastJoin = now; markDirty(); }
    public void currentRank(String value) { currentRank = Objects.requireNonNull(value); markDirty(); }
    public void currentPrestige(String value) { currentPrestige = value; markDirty(); }
    public void autoSell(boolean value) { autoSell = value; markDirty(); }
    public void autoPickup(boolean value) { autoPickup = value; markDirty(); }
    public void autoSmelt(boolean value) { autoSmelt = value; markDirty(); }
    public void autoBlock(boolean value) { autoBlock = value; markDirty(); }

    /** Rolls period counters forward before adding new statistics. */
    public synchronized void rollPeriods(PeriodKeys current) {
        boolean changed = false;
        if (!dailyPeriod.equals(current.daily())) { dailyPeriod = current.daily(); dailyBlocks.set(0); changed = true; }
        if (!weeklyPeriod.equals(current.weekly())) { weeklyPeriod = current.weekly(); weeklyBlocks.set(0); changed = true; }
        if (!monthlyPeriod.equals(current.monthly())) { monthlyPeriod = current.monthly(); monthlyBlocks.set(0); changed = true; }
        if (changed) markDirty();
    }

    public void addBlocks(long amount) {
        if (amount <= 0) return;
        lifetimeBlocks.addAndGet(amount);
        dailyBlocks.addAndGet(amount);
        weeklyBlocks.addAndGet(amount);
        monthlyBlocks.addAndGet(amount);
        markDirty();
    }

    public synchronized void addMoneyEarned(BigDecimal amount) {
        if (amount == null || amount.signum() == 0) return;
        moneyEarned = moneyEarned.add(amount);
        markDirty();
    }

    public void markDirty() { revision.incrementAndGet(); }
    public boolean dirty() { return revision.get() != savedRevision.get(); }
    public long revision() { return revision.get(); }
    public void markSaved(long persistedRevision) { savedRevision.accumulateAndGet(persistedRevision, Math::max); }

    public record PeriodKeys(String daily, String weekly, String monthly) {
        public PeriodKeys {
            Objects.requireNonNull(daily);
            Objects.requireNonNull(weekly);
            Objects.requireNonNull(monthly);
        }
    }
}
