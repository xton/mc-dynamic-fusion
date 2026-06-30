package com.xton.fusion.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Per-key cooldown tracking. The time source is injected so the timing logic
 * can be unit-tested deterministically without sleeping.
 */
public final class CooldownMap {

    private final long cooldownMillis;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public CooldownMap(long cooldownMillis, LongSupplier clock) {
        this.cooldownMillis = cooldownMillis;
        this.clock = clock;
    }

    public CooldownMap(long cooldownMillis) {
        this(cooldownMillis, System::currentTimeMillis);
    }

    /**
     * Returns true and records the use if the key is off cooldown; returns
     * false (without recording) if it is still cooling down.
     */
    public boolean tryUse(UUID key) {
        long now = clock.getAsLong();
        Long previous = lastUse.get(key);
        if (previous != null && now - previous < cooldownMillis) {
            return false;
        }
        lastUse.put(key, now);
        return true;
    }
}
