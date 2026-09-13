package site.mcrelicworld.relicprison.api.model;

import java.math.BigDecimal;

public record RankView(String id, String displayName, int order, BigDecimal nextCost, String mineId) {}
