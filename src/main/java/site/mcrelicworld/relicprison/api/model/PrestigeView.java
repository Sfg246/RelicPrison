package site.mcrelicworld.relicprison.api.model;

import java.math.BigDecimal;

public record PrestigeView(String id, String displayName, int order, BigDecimal cost,
                           BigDecimal sellMultiplier, BigDecimal rankCostMultiplier, String mineId) {}
