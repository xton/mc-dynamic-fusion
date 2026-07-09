package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: how far a worn-armor wearer has to walk to force an early
 * aura pulse, carried in the ID after a colon — {@code DISTANCE:1} pulses
 * again after just a step, {@code DISTANCE:10} only once they've really moved.
 * The companion knob to {@link RateModifier RATE} (the timer side of the same
 * "whichever comes first" trigger — see {@code WornAuraTask}); only
 * meaningful on armor, and falls back to {@code worn.aura-distance-blocks}
 * when absent. A weapon's swing or shot doesn't track distance walked, so
 * DISTANCE does nothing there.
 */
public final class DistanceModifier extends NumericModifier {

    public static final String ID = "DISTANCE";

    public DistanceModifier() {
        this(null);
    }

    private DistanceModifier(Double blocks) {
        super(ID, 0.1, 50.0, blocks);
    }

    @Override
    protected Modifier bind(double value) {
        return new DistanceModifier(value);
    }

    @Override
    protected void apply(WeaponBuilder builder, double value) {
        builder.projectile().setAuraDistanceBlocks(value);
    }

    @Override
    protected String unitSuffix() {
        return " blocks";
    }

    @Override
    public String description() {
        return "sets how far you walk between worn aura pulses";
    }

    @Override
    public String detailedDescription() {
        return "Only meaningful fused onto armor: how many blocks the wearer has to walk to force an early aura pulse, independent of the timer (Rate). A weapon's own swing/shot doesn't track distance walked, so this does nothing there.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }
}
