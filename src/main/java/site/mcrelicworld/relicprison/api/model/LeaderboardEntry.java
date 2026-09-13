package site.mcrelicworld.relicprison.api.model;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaderboardEntry(int position, UUID playerId, String playerName, BigDecimal value) {}
