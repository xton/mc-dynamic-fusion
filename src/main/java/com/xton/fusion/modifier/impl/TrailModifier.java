package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: the inverse of PIERCE for environmental AOEs. Where PIERCE
 * fires the AOE at every <em>occupied</em> cell it passes through, TRAIL fires it
 * at every <em>empty-air</em> cell — so the shot leaves a continuous wake.
 * {@code DEPOSIT:WATER TRAIL} lays a river; {@code FIRE TRAIL} draws a line of
 * flame. Deduped per block cell so a slow shot doesn't re-apply in place.
 */
public final class TrailModifier implements Modifier {

    public static final String ID = "TRAIL";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Trail";
    }

    @Override
    public String description() {
        return "leaves a wake as it flies";
    }

    @Override
    public String detailedDescription() {
        return "Applies the shot's environmental effects at every point of empty air it flies through, not just where it lands. Deposit water for a river, Fire for a line of flame.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().setTrail(true);
    }
}
