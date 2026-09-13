package site.mcrelicworld.relicprison.api.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Read-only player data exposed to other plugins. */
public interface PlayerProfileView {
    UUID uuid();
    String lastName();
    String currentRank();
    String currentPrestige();
    long firstJoin();
    long lastJoin();
    boolean autoSell();
    boolean autoPickup();
    boolean autoSmelt();
    boolean autoBlock();
    long lifetimeBlocks();
    long dailyBlocks();
    long weeklyBlocks();
    long monthlyBlocks();
    String dailyPeriod();
    String weeklyPeriod();
    String monthlyPeriod();
    BigDecimal moneyEarned();
    int dataVersion();
}
