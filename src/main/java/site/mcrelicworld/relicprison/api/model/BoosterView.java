package site.mcrelicworld.relicprison.api.model;

import java.math.BigDecimal;
import java.util.UUID;

public record BoosterView(String id, UUID owner, BigDecimal multiplier, long expiresAt, boolean serverWide) {}
