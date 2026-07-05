package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Worn effect (the Jetpack): fused onto a chestplate or elytra, holding jump
 * while airborne ramps a smooth rise, capped at a max climb speed — a hover,
 * not a one-shot boost. A normal jump from the ground is untouched; releasing
 * jump lets you fall normally. Great on an elytra to climb before gliding out.
 * Acts while worn (see JetpackTask), not on a weapon swing.
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
        return "hold jump to rise";
    }

    @Override
    public String detailedDescription() {
        return "Worn on a chestplate or elytra: hold jump while airborne to rise smoothly, up to a capped speed — a jetpack hover. A grounded jump is unaffected. No effect on a weapon.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        // A worn effect — it acts while equipped (see JetpackTask), not on a swing.
    }
}
