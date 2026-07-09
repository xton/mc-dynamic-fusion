package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: how often a worn-armor aura re-casts, carried in the ID
 * after a colon — {@code RATE:0.5} pulses twice a second, {@code RATE:5} once
 * every five. Only meaningful on armor (see {@code WornAuraTask}, which falls
 * back to {@code worn.aura-period-ticks} when it's absent); a weapon's swing
 * or shot doesn't repeat itself, so RATE does nothing there.
 */
public final class RateModifier extends NumericModifier {

    public static final String ID = "RATE";

    private static final int TICKS_PER_SECOND = 20;

    public RateModifier() {
        this(null);
    }

    private RateModifier(Double seconds) {
        super(ID, 0.1, 60.0, seconds);
    }

    @Override
    protected Modifier bind(double value) {
        return new RateModifier(value);
    }

    @Override
    protected void apply(WeaponBuilder builder, double value) {
        builder.projectile().setAuraRateTicks(Math.max(1, (int) Math.round(value * TICKS_PER_SECOND)));
    }

    @Override
    protected String unitSuffix() {
        return "s";
    }

    @Override
    public String description() {
        return "sets how often a worn aura re-casts";
    }

    @Override
    public String detailedDescription() {
        return "Only meaningful fused onto armor: seconds between aura pulses — low for a rapid-fire aura, high for a slow heartbeat. A weapon's own swing/shot doesn't repeat, so this does nothing there.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }
}
