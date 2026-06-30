package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Widens the effect radius. Stacks: each copy adds {@code bonusPerApply}, so
 * EXPAND×3 makes a much larger burst.
 *
 * <p>Pure: only mutates context.
 */
public final class ExpandModifier implements Modifier {

    public static final String ID = "EXPAND";

    private final double bonusPerApply;

    public ExpandModifier(double bonusPerApply) {
        this.bonusPerApply = bonusPerApply;
    }

    public ExpandModifier() {
        this(3.0);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Expand";
    }

    @Override
    public String description() {
        return "widens the area of effect";
    }

    @Override
    public String detailedDescription() {
        return "Grows the radius of the burst. Stacks — fuse it again for a bigger blast.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        return ctx.addExpandBonus(bonusPerApply);
    }
}
