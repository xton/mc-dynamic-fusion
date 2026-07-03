package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: adds an ICE element — the inverse of FIRE. In its radius it freezes
 * water to ice, sets lava to obsidian, snuffs out fire, and chills the mobs it
 * touches (vanilla powder-snow freeze). Like FIRE/MINING it acts along the
 * flight rather than as a knockback burst. EXPAND widens the freeze.
 */
public final class IceModifier implements Modifier {

    public static final String ID = "ICE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Ice";
    }

    @Override
    public String description() {
        return "freezes the area solid";
    }

    @Override
    public String detailedDescription() {
        return "Freezes water to ice, turns lava to obsidian, puts out fire, and chills mobs in a radius. Add Pierce or Trail to freeze along its path, Expand for a wider chill.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.emitIce();
    }
}
