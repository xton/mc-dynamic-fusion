package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: adds a PUSH burst to the projectile's payload — where it terminates,
 * it shoves nearby entities outward. A small base by itself; scale it with
 * EXPAND (radius) and AMPLIFY (force), flip it with INVERT, or spread it with
 * CHAIN/PERSIST. This is the atom the old monolithic "Nova" is now built from.
 */
public final class PushModifier implements Modifier {

    public static final String ID = "PUSH";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Push";
    }

    @Override
    public String description() {
        return "shoves entities where it lands";
    }

    @Override
    public String detailedDescription() {
        return "Adds a knockback burst to the payload. Scale it with Expand/Amplify, flip it with Invert.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.emitPush();
    }
}
