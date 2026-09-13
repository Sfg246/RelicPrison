package site.mcrelicworld.relicprison.booster;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveBoosterTest {
    @Test void expiresAtItsAbsoluteTimestamp() {
        ActiveBooster booster = new ActiveBooster(
                "id", UUID.randomUUID(), new BigDecimal("2"), 2000, 1000, false, "test");
        assertFalse(booster.expired(1999));
        assertTrue(booster.expired(2000));
    }
}
