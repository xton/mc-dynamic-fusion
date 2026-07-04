package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: adds a FIRE element — an environmental burst that sets the area
 * alight. In its radius it spreads real (spreading) fire on open ground, melts
 * snow and ice, and ignites the mobs it touches. Like MINING it acts along the
 * flight (at the terminus, at each pierced cell, and — with TRAIL — through the
 * air it flies), not as a knockback burst. EXPAND widens the blaze.
 */
public final class FireModifier implements Modifier {

    public static final String ID = "FIRE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Fire";
    }

    @Override
    public String description() {
        return "sets the area alight";
    }

    @Override
    public String detailedDescription() {
        return "Spreads fire, melts snow and ice, and ignites mobs in a radius (a little wider than Mining). Add Pierce or Trail to lay fire along its path, Expand for a bigger blaze.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.emitFire();
    }
}
