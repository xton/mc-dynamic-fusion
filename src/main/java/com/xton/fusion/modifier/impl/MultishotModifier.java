package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: launches more copies of the projectile. Stacks: each copy
 * adds {@code countPerApply} extra projectiles (base is one). Pair with SPREAD
 * for a shotgun, or leave spread at zero for a tight volley.
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
        return "Fires additional projectiles per shot. Stacks — add Spread to fan them into a shotgun blast.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().addCount(countPerApply);
    }
}
