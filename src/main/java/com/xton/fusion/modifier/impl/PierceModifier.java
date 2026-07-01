package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Makes the shot pierce: instead of stopping at the first block or entity, it
 * passes through everything it can (hitting every entity along the way) and only
 * stops at a block too hard to punch through. Pair a piercing shot with a short
 * DELAYED expiry for a ray gun, or with MINING to bore a tunnel.
 *
 * <p>Pure: only sets a flag. The hardness cutoff comes from config.
 */
public final class PierceModifier implements Modifier {

    public static final String ID = "PIERCE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Pierce";
    }

    @Override
    public String description() {
        return "passes through what it hits";
    }

    @Override
    public String detailedDescription() {
        return "The shot punches through soft blocks and every entity in its path, stopping only at very hard blocks.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        return ctx.setPierce(true);
    }
}
