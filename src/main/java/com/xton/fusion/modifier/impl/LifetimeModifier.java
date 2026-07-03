package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: extends how far the projectile flies before it expires — the
 * "range" primitive. Each copy adds a fixed <em>distance</em> ({@code
 * rangePerApply} blocks), converted to ticks against the shot's current speed,
 * so a single LIFETIME always adds the same tunnel length whether it's a fast
 * melee poke or a slow bow lob. Stack it for a long-range lance.
 */
public final class LifetimeModifier implements Modifier {

    public static final String ID = "LIFETIME";

    private final double rangePerApply;

    public LifetimeModifier(double rangePerApply) {
        this.rangePerApply = rangePerApply;
    }

    public LifetimeModifier() {
        this(12.0);
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
        // Fixed distance ÷ current speed = ticks, so raising a weapon's velocity
        // doesn't inflate its LIFETIME range. (Speed is seeded before compile.)
        double speed = Math.max(0.1, builder.projectile().speed());
        int ticks = Math.max(1, (int) Math.round(rangePerApply / speed));
        builder.projectile().addLifetimeTicks(ticks);
    }
}
