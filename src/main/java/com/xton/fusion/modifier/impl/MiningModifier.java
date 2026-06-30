package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Marks the weapon as a mining ray: on swing it carves an arc of softer blocks
 * ahead of the wielder (see {@code MiningRayBehavior}).
 *
 * <p>Pure: only sets a flag on the context.
 */
public final class MiningModifier implements Modifier {

    public static final String ID = "MINING";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Mining";
    }

    @Override
    public String description() {
        return "carves blocks ahead";
    }

    @Override
    public String detailedDescription() {
        return "On swing, breaks an arc of softer blocks in front of you (obsidian and the like resist).";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        return ctx.setMining(true);
    }
}
