package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Launches more projectiles per shot. Stacks: each copy adds
 * {@code countPerApply} extra projectiles (base is one). Pair it with SPREAD
 * for a shotgun, or leave spread at zero for a tight volley.
 *
 * <p>Pure: only mutates context. The launcher spawns the projectiles.
 */
public final class MultishotModifier implements Modifier {

    public static final String ID = "MULTISHOT";

    private final int countPerApply;

    public MultishotModifier(int countPerApply) {
        this.countPerApply = countPerApply;
    }

    public MultishotModifier() {
        this(2);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Multishot";
    }

    @Override
    public String description() {
        return "launches extra projectiles";
    }

    @Override
    public String detailedDescription() {
        return "Fires additional projectiles per shot. Stacks — add SPREAD to fan them into a shotgun blast.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        return ctx.addCount(countPerApply);
    }
}
