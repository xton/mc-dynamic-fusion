package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Worn effect (the Jetpack): fused onto a chestplate or elytra, jumping fires an
 * upward thrust so you leap and lift off — pair it with an elytra to jump-start a
 * glide, no firework needed. Acts while worn (see JetpackListener), not on a
 * weapon swing.
 */
public final class LiftModifier implements Modifier {

    public static final String ID = "LIFT";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Lift";
    }

    @Override
    public String description() {
        return "jump to thrust upward";
    }

    @Override
    public String detailedDescription() {
        return "Worn on a chestplate or elytra, jumping thrusts you upward — a jetpack hop. Great on an elytra to kick off a glide. No effect on a weapon.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        // A worn effect — it acts while equipped (see JetpackListener), not on a swing.
    }
}
