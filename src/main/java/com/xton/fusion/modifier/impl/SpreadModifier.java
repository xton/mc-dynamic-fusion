package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: scatters aim — each projectile is nudged off the pointing
 * direction by a random angle within a widening cone. Stacks: each copy adds
 * {@code degreesPerApply} to the cone half-angle. With MULTISHOT it fans the
 * volley into a spray.
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
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().addSpreadDegrees(degreesPerApply);
    }
}
