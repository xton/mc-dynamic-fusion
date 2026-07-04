package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ParameterizedModifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: sets the shot's launch speed to an <em>absolute</em> value
 * (blocks/tick), carried in the ID after a colon — {@code SPEED:0.6} for a slow
 * lob, {@code SPEED:3} for a fast bolt. Unlike a scaling transform this pins the
 * speed outright, so it composes predictably with GRAVITY and a bundle. The
 * registry holds one bare {@code SPEED} template; {@link #withParameter} mints
 * the concrete, value-bound instance.
 */
public final class SpeedModifier implements ParameterizedModifier {

    public static final String ID = "SPEED";

    /** Clamp so a stray value can't stall the shot or send it across the world in a tick. */
    private static final double MIN = 0.1;
    private static final double MAX = 6.0;

    /** The speed this instance sets, or null for the bare (inert) template. */
    private final Double speed;

    public SpeedModifier() {
        this(null);
    }

    private SpeedModifier(Double speed) {
        this.speed = speed;
    }

    @Override
    public Modifier withParameter(String param) {
        Double v = parse(param);
        return v == null ? null : new SpeedModifier(v);
    }

    private static Double parse(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        try {
            double v = Double.parseDouble(param.trim());
            return Math.min(MAX, Math.max(MIN, v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String id() {
        return speed == null ? ID : ID + ":" + Format.number(speed);
    }

    @Override
    public String displayName() {
        return speed == null ? "Speed" : "Speed " + Format.number(speed);
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

    @Override
    public void apply(WeaponBuilder builder) {
        if (speed != null) {
            builder.projectile().setSpeed(speed);
        }
    }
}
