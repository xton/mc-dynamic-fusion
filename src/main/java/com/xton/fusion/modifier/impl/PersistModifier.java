package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Leaves a lingering field at the burst point that re-applies the effect on a
 * pulse for a while. Stacks: each copy adds {@code ticksPerApply} of duration.
 *
 * <p>Pure: only accumulates a duration. The behaviour layer schedules pulses.
 */
public final class PersistModifier implements Modifier {

    public static final String ID = "PERSIST";

    private final int ticksPerApply;

    public PersistModifier(int ticksPerApply) {
        this.ticksPerApply = ticksPerApply;
    }

    public PersistModifier() {
        this(60);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Persist";
    }

    @Override
    public String description() {
        return "leaves a lingering field";
    }

    @Override
    public String detailedDescription() {
        return "Drops a zone at the burst point that keeps pulsing the effect. Stacks for a longer-lasting field.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        // PERSIST re-pulses a burst, so it opts one in.
        return ctx.enableBurst().addPersistTicks(ticksPerApply);
    }
}
