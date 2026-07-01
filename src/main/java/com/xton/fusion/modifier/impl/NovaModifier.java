package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Releases a burst that affects all directions equally from the origin.
 *
 * <p>Pure: it only sets effect parameters on the context. The actual world
 * interaction (finding and pushing entities) lives in the behaviour layer.
 */
public final class NovaModifier implements Modifier {

    public static final String ID = "NOVA";

    private final double radius;
    private final double power;

    public NovaModifier(double radius, double power) {
        this.radius = radius;
        this.power = power;
    }

    public NovaModifier() {
        this(4.0, 1.4);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Nova";
    }

    @Override
    public String description() {
        return "affects all directions equally";
    }

    @Override
    public String detailedDescription() {
        return "Releases a burst that pushes everything outward from you.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        ctx.enableBurst();
        ctx.setRadial(true);
        ctx.setRadius(radius);
        // Multiply by the running amplifier so a preceding AMPLIFY compounds.
        ctx.setPower(power * ctx.getAmplifier());
        return ctx;
    }
}
