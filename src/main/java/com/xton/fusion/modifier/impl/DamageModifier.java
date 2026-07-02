package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: adds a DAMAGE burst to the projectile's payload — where it
 * terminates, it hurts nearby entities. Small base; stack AMPLIFY to hit
 * harder, EXPAND to hit wider, CHAIN to leap to more foes, PERSIST to keep
 * dealing damage in a lingering field.
 */
public final class DamageModifier implements Modifier {

    public static final String ID = "DAMAGE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Damage";
    }

    @Override
    public String description() {
        return "harms entities where it lands";
    }

    @Override
    public String detailedDescription() {
        return "Adds a damaging burst to the payload. Stack Amplify to hit harder, Expand to hit wider.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.emitDamage();
    }
}
