package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: sets the shot's launch speed to an <em>absolute</em> value
 * (blocks/tick), carried in the ID after a colon — {@code SPEED:0.6} for a slow
 * lob, {@code SPEED:3} for a fast bolt. Unlike a scaling transform this pins the
 * speed outright, so it composes predictably with GRAVITY and a bundle. Clamped
 * so a stray value can't stall the shot or send it across the world in a tick.
 */
public final class SpeedModifier extends NumericModifier {

    public static final String ID = "SPEED";

    public SpeedModifier() {
        this(null);
    }

    private SpeedModifier(Double speed) {
        super(ID, 0.1, 6.0, speed);
    }

    @Override
    protected Modifier bind(double value) {
        return new SpeedModifier(value);
    }

    @Override
    protected void apply(WeaponBuilder builder, double value) {
        builder.projectile().setSpeed(value);
    }

    @Override
    public String description() {
        return "sets launch speed";
    }

    @Override
    public String detailedDescription() {
        return "Pins the shot's launch speed (blocks/tick) to an exact value — low for a slow lob, high for a fast bolt.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }
}
