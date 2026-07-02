package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: makes the projectile pierce — instead of stopping at the
 * first block or entity, it passes through everything it can (hitting every
 * entity along the way) and stops only at a block too hard to punch through.
 * Pair with a short LIFETIME for a ray gun, or MINING to bore a tunnel.
 */
public final class PierceModifier implements Modifier {

    public static final String ID = "PIERCE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Pierce";
    }

    @Override
    public String description() {
        return "passes through what it hits";
    }

    @Override
    public String detailedDescription() {
        return "The shot punches through soft blocks and every entity in its path, stopping only at very hard blocks.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().setPierce(true);
    }
}
