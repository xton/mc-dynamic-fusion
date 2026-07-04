package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: adds a PULL burst — the complement of PUSH. Where it lands it drags
 * nearby entities <em>inward</em> instead of shoving them out (a vacuum). This is
 * an explicit atom rather than PUSH + INVERT; scale it with AMPLIFY/EXPAND the
 * same way. Great for gathering mobs before a DAMAGE or DELAY blast.
 */
public final class PullModifier implements Modifier {

    public static final String ID = "PULL";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Pull";
    }

    @Override
    public String description() {
        return "drags entities inward";
    }

    @Override
    public String detailedDescription() {
        return "Adds a vacuum burst — the opposite of Push. Gathers entities toward the point where it lands. Pair with Damage or Delay for a lure-then-blast.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.emitPull();
    }
}
