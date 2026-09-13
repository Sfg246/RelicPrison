package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.util.UUID;

public record GangLeaderboardEntry(int position, UUID gangId, String gangName, BigDecimal value) { }
