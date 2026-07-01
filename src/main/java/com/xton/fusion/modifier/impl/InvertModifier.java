package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Transform: inverts the nearest preceding PUSH burst so it pulls entities
 * inward (an implosion) instead of shoving them out. Toggles — two INVERTs
 * cancel. Inert without a burst before it (and harmless on a DAMAGE burst).
 *
 * <p>Backfire by design: an inverted push on your own swing yanks mobs onto you
 * (a mob magnet). Faithful to what you built, not a malfunction.
 */
public final class InvertModifier implements Modifier {

    public static final String ID = "INVERT";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Invert";
    }

    @Override
    public String description() {
        return "pulls the burst inward";
    }

    @Override
    public String detailedDescription() {
        return "Turns the shove before it into an implosion that drags entities toward the centre. Careful — that centre might be you.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        AoeSpec target = builder.topAoe();
        if (target != null) {
            target.toggleInvert();
        }
    }
}
