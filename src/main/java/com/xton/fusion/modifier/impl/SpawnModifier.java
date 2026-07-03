package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: spawns a fresh child projectile at the current projectile's terminus.
 * The child inherits <em>nothing</em> — it starts from base flight with an empty
 * payload — and every modifier <em>after</em> SPAWN builds the child instead of
 * the parent. So {@code DAMAGE SPAWN MULTISHOT SPREAD FIRE} is a bolt that, where
 * it lands, bursts into a fanned volley of fiery children (a cluster bomb).
 *
 * <p>Recursion is capped by config ({@code spawn.max-generation}); past the cap a
 * SPAWN simply doesn't fire, so a self-referential stack can't run away.
 */
public final class SpawnModifier implements Modifier {

    public static final String ID = "SPAWN";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Spawn";
    }

    @Override
    public String description() {
        return "bursts into new projectiles";
    }

    @Override
    public String detailedDescription() {
        return "Where the shot lands, it launches a fresh projectile built from every modifier after Spawn. Pair with Multishot/Spread for a cluster bomb. Nesting is capped so it can't run away.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.emitSpawn();
    }
}
