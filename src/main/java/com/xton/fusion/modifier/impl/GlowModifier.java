package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Worn effect: fused onto a piece of armor, it makes the wearer glow — a strong,
 * persistent Glowing effect (the vanilla see-through-walls outline), not night
 * vision — for others to see. Since Minecraft never renders your own body in
 * first person, the wearer gets their own half too: a light only they can see
 * (client-side, not placed in the world) tracked just in front of their face
 * (see {@link com.xton.fusion.wearable.GlowLightTask}), so they can actually
 * see by it. Unlike the weapon modifiers it does nothing
 * on a swing; worn-effect tasks apply it while the armor is equipped.
 * (Harmless if fused onto a weapon — it just won't do anything.)
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
        return "makes you glow and lights your way";
    }

    @Override
    public String detailedDescription() {
        return "Worn as armor: gives you a powerful glowing outline for others to see, and a light only you can see tracked in front of your face so you can see too. No effect on a weapon.";
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
