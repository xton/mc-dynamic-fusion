package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: turns gravity ON, so the shot arcs and drops like a lob
 * instead of flying dead straight. Weapon type still sets the default (a bow
 * already arcs, a melee poke doesn't); this lets any weapon — a sword, say —
 * throw an arcing shot without a bow's draw-back. Pair with a slow SPEED and
 * VISIBLE for a proper mortar.
 */
public final class GravityModifier implements Modifier {

    public static final String ID = "GRAVITY";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Gravity";
    }

    @Override
    public String description() {
        return "arcs and drops like a lob";
    }

    @Override
    public String detailedDescription() {
        return "The shot falls under gravity, arcing to the ground instead of flying straight — a lob from any weapon. Slow it down (Speed) and make it Visible for a mortar.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().setGravity(true);
    }
}
