package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: the Golden Brush's magic. On a fused brush, each TREASURE (fused from
 * gold) raises the loot level — brushing <em>anything</em> then has a growing
 * chance to cough up an item, and the more gold you pile on, the rarer the loot
 * that can appear. Does nothing on a non-brush weapon.
 */
public final class TreasureModifier implements Modifier {

    public static final String ID = "TREASURE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Treasure";
    }

    @Override
    public String description() {
        return "brush anything for loot";
    }

    @Override
    public String detailedDescription() {
        return "On a brush, sweeping any block may cough up an item. Fuse more gold to proc more often and unlock rarer finds.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().addTreasure(1);
    }
}
