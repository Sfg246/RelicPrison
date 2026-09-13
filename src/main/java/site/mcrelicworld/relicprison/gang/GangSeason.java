package site.mcrelicworld.relicprison.gang;

import java.util.List;

public record GangSeason(String id, long startsAt, long endsAt, List<String> categories,
                         String rewardPlan, State state, long createdAt, Long finalizedAt) {
    public GangSeason { categories = List.copyOf(categories); }
    public enum State { SCHEDULED, ACTIVE, FINALIZED, CANCELLED }
}
