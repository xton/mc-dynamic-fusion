package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: like SPAWN, but the child doesn't fire at the terminus — it arms
 * there instead, blinking in place, and watches for a nearby creature within
 * range. The moment one steps in, it detonates with whatever payload the
 * modifiers after DETECT built (a trap/mine). EXPAND, placed right after
 * DETECT, widens that trigger radius exactly like it widens a MINING bore; a
 * mine that never triggers disarms quietly after a while instead of sitting
 * forever.
 */
public final class DetectModifier implements Modifier {

    public static final String ID = "DETECT";

    private final double baseRange;
    private final int maxWaitTicks;

    public DetectModifier(double baseRange, int maxWaitTicks) {
        this.baseRange = baseRange;
        this.maxWaitTicks = maxWaitTicks;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Detect";
    }

    @Override
    public String description() {
        return "arms and waits for a nearby creature";
    }

    @Override
    public String detailedDescription() {
        return "Like Spawn, but the child doesn't fire at the terminus — it arms there instead, blinking in place, and detonates the moment a creature steps within range (a trap/mine). Expand widens the trigger radius. An untriggered mine disarms quietly after a while.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.emitDetect(baseRange, maxWaitTicks);
    }
}
