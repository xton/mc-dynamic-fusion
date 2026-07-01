package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Transform: lets the nearest preceding burst hop to additional nearby entities
 * beyond its main area. Stacks: each copy adds {@code countPerApply} more hops.
 * Inert without an emitter before it.
 */
public final class ChainModifier implements Modifier {

    public static final String ID = "CHAIN";

    private final int countPerApply;

    public ChainModifier(int countPerApply) {
        this.countPerApply = countPerApply;
    }

    public ChainModifier() {
        this(2);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Chain";
    }

    @Override
    public String description() {
        return "burst jumps to more entities";
    }

    @Override
    public String detailedDescription() {
        return "After the burst before it lands, the effect leaps to the nearest entities one after another.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        AoeSpec target = builder.topAoe();
        if (target != null) {
            target.addChain(countPerApply);
        }
    }
}
