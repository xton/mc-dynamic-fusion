package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: teleports the caster to where the shot terminates. Only the
 * <em>first</em> bolt of a cast to terminate moves you — a MULTISHOT volley or a
 * SPAWN cluster still teleports once — and you land safely offset out of blocks
 * and mobs. Pair with PIERCE to blink to the far end of a tunnel.
 */
public final class TeleportModifier implements Modifier {

    public static final String ID = "TELEPORT";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Teleport";
    }

    @Override
    public String description() {
        return "warps you to where it lands";
    }

    @Override
    public String detailedDescription() {
        return "The caster is teleported to where the shot terminates (once per cast, safely offset out of walls). Add Pierce to blink to the end of a bored tunnel.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().setTeleport(true);
    }
}
