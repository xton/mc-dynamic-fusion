package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ParameterizedModifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: like SPAWN, but the fresh child waits {@code n} seconds after the
 * parent's terminus and then detonates <em>in place</em> (it starts stationary
 * with a ~zero lifetime). Every modifier after {@code DELAY:n} builds that child,
 * so {@code PULL DELAY:2 DAMAGE} gathers mobs, waits two seconds, then blasts them
 * once clustered. The delay is carried in the ID after a colon; the registry
 * holds one bare {@code DELAY} template.
 */
public final class DelayModifier implements ParameterizedModifier {

    public static final String ID = "DELAY";

    private static final double MIN_SECONDS = 0.05;
    private static final double MAX_SECONDS = 30.0;
    private static final int TICKS_PER_SECOND = 20;

    /** The delay in seconds this instance sets, or null for the bare template. */
    private final Double seconds;

    public DelayModifier() {
        this(null);
    }

    private DelayModifier(Double seconds) {
        this.seconds = seconds;
    }

    @Override
    public Modifier withParameter(String param) {
        Double v = parse(param);
        return v == null ? null : new DelayModifier(v);
    }

    private static Double parse(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        try {
            double v = Double.parseDouble(param.trim());
            return Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String id() {
        return seconds == null ? ID : ID + ":" + Format.number(seconds);
    }

    @Override
    public String displayName() {
        return seconds == null ? "Delay" : "Delay " + Format.number(seconds) + "s";
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

    @Override
    public void apply(WeaponBuilder builder) {
        if (seconds != null) {
            builder.emitDelay(Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND)));
        }
    }
}
