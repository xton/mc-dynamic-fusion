package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Makes the effect fire extra times in rapid succession. Stacks: each copy
 * adds {@code countPerApply} more repeats.
 *
 * <p>Pure: only mutates context. The behaviour layer schedules the repeats.
 */
public final class RepeatModifier implements Modifier {

    public static final String ID = "REPEAT";

    private final int countPerApply;

    public RepeatModifier(int countPerApply) {
        this.countPerApply = countPerApply;
    }

    public RepeatModifier() {
        this(2);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Repeat";
    }

    @Override
    public String description() {
        return "fires several times in a row";
    }

    @Override
    public String detailedDescription() {
        return "Triggers the effect again a few times in quick succession.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        return ctx.addRepeatCount(countPerApply);
    }
}
