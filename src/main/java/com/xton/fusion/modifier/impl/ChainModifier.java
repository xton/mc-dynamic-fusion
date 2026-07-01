package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Lets the effect hop to additional nearby entities beyond the main burst.
 * Stacks: each copy adds {@code countPerApply} more hops.
 *
 * <p>Pure: only mutates context.
 */
public final class ChainModifier implements Modifier {

    public static final String ID = "CHAIN";

    private final int countPerApply;

    public ChainModifier(int countPerApply) {
        this.countPerApply = countPerApply;
    }

    public ChainModifier() {
        this(3);
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
        return "jumps to nearby entities";
    }

    @Override
    public String detailedDescription() {
        return "After the burst, the effect leaps to the nearest entities one after another.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        // CHAIN hops out of a burst, so it opts one in.
        return ctx.enableBurst().addChainCount(countPerApply);
    }
}
