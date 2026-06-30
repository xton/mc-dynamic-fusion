package com.xton.fusion.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Pure: time is injected, so cooldown timing is tested without sleeping. */
class CooldownMapTest {

    @Test
    void blocksWithinWindowAndAllowsAfter() {
        long[] now = {1_000L};
        CooldownMap cooldown = new CooldownMap(200, () -> now[0]);
        UUID id = UUID.randomUUID();

        assertTrue(cooldown.tryUse(id), "first use should pass");
        assertFalse(cooldown.tryUse(id), "immediate repeat should be blocked");

        now[0] = 1_199L; // 199ms later — still inside the window
        assertFalse(cooldown.tryUse(id));

        now[0] = 1_200L; // exactly 200ms later — off cooldown
        assertTrue(cooldown.tryUse(id));
    }

    @Test
    void tracksKeysIndependently() {
        long[] now = {0L};
        CooldownMap cooldown = new CooldownMap(200, () -> now[0]);

        assertTrue(cooldown.tryUse(UUID.randomUUID()));
        assertTrue(cooldown.tryUse(UUID.randomUUID()));
    }
}
