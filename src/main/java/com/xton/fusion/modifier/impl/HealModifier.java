package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: adds a HEAL burst — the complement of DAMAGE. Where it lands it
 * restores health to nearby friendly entities (players, tame/passive mobs, and
 * the caster), leaving hostiles alone. Scale it with AMPLIFY (more health) and
 * EXPAND (bigger radius), just like DAMAGE. A counterintuitive "heal bomb" when
 * paired with MULTISHOT/PERSIST.
 */
public final class HealModifier implements Modifier {

    public static final String ID = "HEAL";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Heal";
    }

    @Override
    public String description() {
        return "restores health where it lands";
    }

    @Override
    public String detailedDescription() {
        return "Adds a healing burst — the opposite of Damage. Mends players, friendly mobs, and you; hostiles are left out. Scale with Amplify/Expand.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.emitHeal();
    }
}
