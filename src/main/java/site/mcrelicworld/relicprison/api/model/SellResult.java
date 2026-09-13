package site.mcrelicworld.relicprison.api.model;

import java.math.BigDecimal;

public record SellResult(
        boolean success,
        BigDecimal baseValue,
        BigDecimal finalValue,
        int itemCount,
        String error
) {
    public static SellResult failure(String error) {
        return new SellResult(false, BigDecimal.ZERO, BigDecimal.ZERO, 0, error);
    }
}
