package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: the shot steers toward the nearest creature as it flies,
 * curving to chase rather than snapping onto its target. Each stack sharpens the
 * turn, so more HOMING tracks harder. Pair with LIFETIME so it has time to run
 * something down, or MULTISHOT so each bolt seeks on its own.
 */
public final class HomingModifier implements Modifier {

    public static final String ID = "HOMING";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Homing";
    }

    @Override
    public String description() {
        return "curves toward nearby creatures";
    }

    @Override
    public String detailedDescription() {
        return "The shot turns to chase the nearest creature mid-flight; it re-targets if one dies or slips away. Stack for a sharper turn, add Lifetime to give it room to hunt.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().addHoming(1);
    }
}
