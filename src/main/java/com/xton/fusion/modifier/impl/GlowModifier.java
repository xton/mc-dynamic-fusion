package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Worn effect: fused onto a piece of armor, it makes the wearer glow — a strong,
 * persistent Glowing effect (the vanilla see-through-walls outline), not night
 * vision. Unlike the weapon modifiers it does nothing on a swing; the
 * worn-effect task applies it while the armor is equipped. (Harmless if fused
 * onto a weapon — it just won't do anything.)
 */
public final class GlowModifier implements Modifier {

    public static final String ID = "GLOW";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Glow";
    }

    @Override
    public String description() {
        return "makes you glow";
    }

    @Override
    public String detailedDescription() {
        return "Worn as armor, gives you a powerful glowing outline for as long as it's equipped. No effect on a weapon.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        // A worn effect — it acts while equipped (see WornEffectTask), not on a swing.
    }
}
