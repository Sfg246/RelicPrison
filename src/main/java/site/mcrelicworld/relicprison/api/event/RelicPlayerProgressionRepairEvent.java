package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RelicPlayerProgressionRepairEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String lastKnownPlayerName;
    private final Set<String> previousDirectRankGroups;
    private final Set<String> previousDirectPrestigeGroups;
    private final String expectedRank;
    private final String expectedPrestige;
    private final List<String> repairsPerformed;
    private final String repairReason;
    private final boolean automatic;

    public RelicPlayerProgressionRepairEvent(UUID playerId, String lastKnownPlayerName,
                                             Set<String> previousDirectRankGroups,
                                             Set<String> previousDirectPrestigeGroups,
                                             String expectedRank, String expectedPrestige,
                                             List<String> repairsPerformed, String repairReason,
                                             boolean automatic) {
        this.playerId = playerId;
        this.lastKnownPlayerName = lastKnownPlayerName;
        this.previousDirectRankGroups = Set.copyOf(previousDirectRankGroups);
        this.previousDirectPrestigeGroups = Set.copyOf(previousDirectPrestigeGroups);
        this.expectedRank = expectedRank;
        this.expectedPrestige = expectedPrestige;
        this.repairsPerformed = List.copyOf(repairsPerformed);
        this.repairReason = repairReason;
        this.automatic = automatic;
    }

    public UUID playerId() { return playerId; }
    public String lastKnownPlayerName() { return lastKnownPlayerName; }
    public Set<String> previousDirectRankGroups() { return previousDirectRankGroups; }
    public Set<String> previousDirectPrestigeGroups() { return previousDirectPrestigeGroups; }
    public String expectedRank() { return expectedRank; }
    public String expectedPrestige() { return expectedPrestige; }
    public List<String> repairsPerformed() { return repairsPerformed; }
    public String repairReason() { return repairReason; }
    public boolean automatic() { return automatic; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
