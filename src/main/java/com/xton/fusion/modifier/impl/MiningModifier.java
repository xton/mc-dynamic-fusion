package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ProjectileSpec;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: turns the projectile into a mining ray — it pierces and
 * breaks the softer blocks it passes through, flying fast with a short life so
 * it carves a stub of a tunnel ahead. A "true mining ray" is exactly this:
 * pierce plus a very short expiry, built from the same primitives as everything
 * else. Stack LIFETIME to reach farther. Its payload is separate — a bare
 * mining ray delivers no burst at its terminus.
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
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        ProjectileSpec p = builder.projectile();
        p.setMining(true);
        p.setPierce(true);
        p.setSpeed(speed);
        p.setLifetimeTicks(lifetimeTicks);
        p.setPierceMaxHardness(maxHardness);
    }
}
