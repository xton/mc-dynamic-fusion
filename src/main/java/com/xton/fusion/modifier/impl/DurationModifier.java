package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: sets the shot's lifetime to an <em>absolute</em> duration in
 * seconds, carried in the ID after a colon — {@code DURATION:3}. Unlike LIFETIME
 * (which <em>adds</em> a fixed range), this pins how long the shot lives outright
 * — handy for a lob, or a time-capped BOUNCE.
 */
public final class DurationModifier extends NumericModifier {

    public static final String ID = "DURATION";

    private static final int TICKS_PER_SECOND = 20;

    public DurationModifier() {
        this(null);
    }

    private DurationModifier(Double seconds) {
        super(ID, 0.05, 30.0, seconds);
    }

    @Override
    protected Modifier bind(double value) {
        return new DurationModifier(value);
    }

    @Override
    protected void apply(WeaponBuilder builder, double value) {
        builder.projectile().setLifetimeTicks(Math.max(1, (int) Math.round(value * TICKS_PER_SECOND)));
    }

    @Override
    protected String unitSuffix() {
        return "s";
    }

    @Override
    public String description() {
        return "sets how long it lives";
    }

    @Override
    public String detailedDescription() {
        return "Pins the shot's lifetime to an exact number of seconds before it expires and goes off — unlike Lifetime, which adds range. Good for lobs and timed bounces.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }
}
