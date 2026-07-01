package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Scatters aim: each projectile is nudged off the pointing direction by a random
 * angle within a widening cone. Stacks: each copy adds {@code degreesPerApply}
 * to the cone half-angle. On its own it makes a shot inaccurate; with MULTISHOT
 * it fans the volley into a spray.
 *
 * <p>Pure: only mutates context. The launcher applies the random offset.
 */
public final class SpreadModifier implements Modifier {

    public static final String ID = "SPREAD";

    private final double degreesPerApply;

    public SpreadModifier(double degreesPerApply) {
        this.degreesPerApply = degreesPerApply;
    }

    public SpreadModifier() {
        this(12.0);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Spread";
    }

    @Override
    public String description() {
        return "scatters the aim";
    }

    @Override
    public String detailedDescription() {
        return "Widens the cone each projectile can veer into. Stacks — with Multishot it becomes a spray.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        return ctx.addSpreadDegrees(degreesPerApply);
    }
}
