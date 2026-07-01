package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: extends how long the projectile lives before it expires and
 * terminates — the "expiry" primitive. Stacks: each copy adds
 * {@code ticksPerApply} more life, so the shot travels farther. Leave it short
 * for a hit-where-it-lands bolt, or stack it for a long-range lance.
 */
public final class LifetimeModifier implements Modifier {

    public static final String ID = "LIFETIME";

    private final int ticksPerApply;

    public LifetimeModifier(int ticksPerApply) {
        this.ticksPerApply = ticksPerApply;
    }

    public LifetimeModifier() {
        this(30);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Lifetime";
    }

    @Override
    public String description() {
        return "flies longer before it goes off";
    }

    @Override
    public String detailedDescription() {
        return "Lengthens how long the shot lives, so it travels farther before it expires and terminates. Stacks for more range.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().addLifetimeTicks(ticksPerApply);
    }
}
