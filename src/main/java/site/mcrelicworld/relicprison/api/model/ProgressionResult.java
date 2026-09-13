package site.mcrelicworld.relicprison.api.model;

import java.math.BigDecimal;

public record ProgressionResult(boolean success, String code, String oldRank, String newRank,
                                String oldPrestige, String newPrestige, BigDecimal cost) {
    public static ProgressionResult failure(String code) {
        return new ProgressionResult(false, code, null, null, null, null, BigDecimal.ZERO);
    }
}
