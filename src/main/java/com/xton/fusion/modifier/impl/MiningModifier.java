package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: adds a MINING element — a block-breaking tunnel of base radius 1 (one
 * block wide) carved along the flight. EXPAND widens the bore (it scales the
 * nearest preceding emitter, i.e. this one). MINING does <em>not</em> pierce on
 * its own: alone it breaks the block it hits and stops; add PIERCE to bore
 * through many, and LIFETIME to reach farther. Its work happens along the path,
 * so a bare mining ray delivers no burst at its terminus.
 */
public final class MiningModifier implements Modifier {

    public static final String ID = "MINING";

    private final double baseRadius;
    private final double baseHardness;
    private final double hardnessPerApply;

    public MiningModifier(double baseRadius, double baseHardness, double hardnessPerApply) {
        this.baseRadius = baseRadius;
        this.baseHardness = baseHardness;
        this.hardnessPerApply = hardnessPerApply;
    }

    public MiningModifier(double baseRadius) {
        this(baseRadius, 3.0, 15.0);
    }

    public MiningModifier() {
        this(1.0);
    }

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
        return "carves the blocks it hits";
    }

    @Override
    public String detailedDescription() {
        return "Breaks blocks along its path (harder ones like obsidian resist at first). Stack Mining (or Amplify) to chew harder blocks, Pierce to bore through many, Expand for a wider tunnel.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.emitMining(baseRadius, baseHardness, hardnessPerApply);
    }
}
