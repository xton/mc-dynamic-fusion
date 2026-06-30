package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Delays the swing effect by a short fuse. Stacks: each copy adds
 * {@code ticksPerApply} more delay.
 *
 * <p>Pure: only mutates context. The behaviour layer schedules the delay.
 */
public final class DelayedModifier implements Modifier {

    public static final String ID = "DELAYED";

    private final int ticksPerApply;

    public DelayedModifier(int ticksPerApply) {
        this.ticksPerApply = ticksPerApply;
    }

    public DelayedModifier() {
        this(30);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Delayed";
    }

    @Override
    public String description() {
        return "fires after a short fuse";
    }

    @Override
    public String detailedDescription() {
        return "Holds the effect, then unleashes it a moment later. Stacks for a longer fuse.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        return ctx.addDelayTicks(ticksPerApply);
    }
}
