package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Extends how long a projectile flies before it expires and triggers — the
 * "expiry" primitive. Stacks: each copy adds {@code ticksPerApply} more life,
 * so the shot travels farther before it goes off. Leave it short for a
 * hit-where-it-lands bolt, or stack it for a long-range lance.
 *
 * <p>Pure: only mutates context. The projectile layer honours the lifetime.
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
        return "flies longer before it goes off";
    }

    @Override
    public String detailedDescription() {
        return "Lengthens the fuse so the shot travels farther before it expires and triggers. Stacks for more range.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        return ctx.addLifetimeTicks(ticksPerApply);
    }
}
