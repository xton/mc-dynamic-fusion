package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Turns the shot into a mining ray: it pierces and breaks the softer blocks it
 * passes through, flying fast with a short life so it carves a stub of a tunnel
 * ahead. A "true mining ray" is exactly this — pierce plus a very short expiry —
 * built from the same primitives everything else uses. Stack LIFETIME to reach
 * farther; raise the pierce hardness (config) to chew tougher stone.
 *
 * <p>Pure: only sets flags and the mining spec on the context.
 */
public final class MiningModifier implements Modifier {

    public static final String ID = "MINING";

    private final int lifetimeTicks;
    private final double speed;
    private final double maxHardness;

    public MiningModifier(int lifetimeTicks, double speed, double maxHardness) {
        this.lifetimeTicks = lifetimeTicks;
        this.speed = speed;
        this.maxHardness = maxHardness;
    }

    public MiningModifier() {
        this(6, 2.5, 3.0);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Mining";
    }

    @Override
    public String description() {
        return "carves soft blocks ahead";
    }

    @Override
    public String detailedDescription() {
        return "A short, fast piercing ray that breaks the softer blocks it passes through (obsidian and the like resist).";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        return ctx.setMining(true)
                .setPierce(true)
                .setSpeed(speed)
                .setLifetimeTicks(lifetimeTicks)
                .setPierceMaxHardness(maxHardness);
    }
}
