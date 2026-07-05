package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ParameterizedModifier;
import com.xton.fusion.modifier.WeaponBuilder;
import com.xton.fusion.util.Format;

/**
 * Flight transform: sets the shot's lifetime to an <em>absolute</em> duration in
 * seconds, carried in the ID after a colon — {@code DURATION:3}. Unlike LIFETIME
 * (which <em>adds</em> a fixed range), this pins how long the shot lives outright
 * — handy for a lob, or a time-capped BOUNCE. The registry holds one bare
 * {@code DURATION} template; {@link #withParameter} mints the concrete instance.
 */
public final class DurationModifier implements ParameterizedModifier {

    public static final String ID = "DURATION";

    private static final double MIN_SECONDS = 0.05;
    private static final double MAX_SECONDS = 30.0;
    private static final int TICKS_PER_SECOND = 20;

    /** The lifetime in seconds this instance sets, or null for the bare template. */
    private final Double seconds;

    public DurationModifier() {
        this(null);
    }

    private DurationModifier(Double seconds) {
        this.seconds = seconds;
    }

    @Override
    public Modifier withParameter(String param) {
        Double v = parse(param);
        return v == null ? null : new DurationModifier(v);
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
        return seconds == null ? "Duration" : "Duration " + Format.number(seconds) + "s";
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

    @Override
    public void apply(WeaponBuilder builder) {
        if (seconds != null) {
            builder.projectile().setLifetimeTicks(Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND)));
        }
    }
}
