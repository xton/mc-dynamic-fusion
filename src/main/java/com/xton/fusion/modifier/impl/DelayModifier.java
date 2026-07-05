package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: like SPAWN, but the fresh child waits {@code n} seconds after the
 * parent's terminus and then detonates <em>in place</em> (it starts stationary
 * with a ~zero lifetime). Every modifier after {@code DELAY:n} builds that child,
 * so {@code PULL DELAY:2 DAMAGE} gathers mobs, waits two seconds, then blasts them
 * once clustered.
 */
public final class DelayModifier extends NumericModifier {

    public static final String ID = "DELAY";

    private static final int TICKS_PER_SECOND = 20;

    public DelayModifier() {
        this(null);
    }

    private DelayModifier(Double seconds) {
        super(ID, 0.05, 30.0, seconds);
    }

    @Override
    protected Modifier bind(double value) {
        return new DelayModifier(value);
    }

    @Override
    protected void apply(WeaponBuilder builder, double value) {
        builder.emitDelay(Math.max(1, (int) Math.round(value * TICKS_PER_SECOND)));
    }

    @Override
    protected String unitSuffix() {
        return "s";
    }

    @Override
    public String description() {
        return "waits, then goes off in place";
    }

    @Override
    public String detailedDescription() {
        return "Spawns a fresh charge that waits the set seconds, then detonates where the shot landed. Everything after Delay builds that charge — Pull, wait, then Damage for a lure-and-blast.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }
}
