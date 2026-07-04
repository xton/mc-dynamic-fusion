package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: the shot ricochets off blocks instead of going off on them.
 * It reflects off each surface it hits (shedding a little speed) and keeps
 * flying, <em>only</em> triggering its payload when it finally expires or hits a
 * mob directly. Pair with LIFETIME to keep it bouncing longer, or with SPAWN for
 * a grenade that scatters children where it eventually comes to rest.
 */
public final class BounceModifier implements Modifier {

    public static final String ID = "BOUNCE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Bounce";
    }

    @Override
    public String description() {
        return "ricochets off blocks";
    }

    @Override
    public String detailedDescription() {
        return "The shot bounces off blocks instead of triggering on them — it only goes off when it expires or strikes a mob. Add Lifetime for more bounces, or Spawn for a scattering grenade.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().addBounces(1);
    }
}
