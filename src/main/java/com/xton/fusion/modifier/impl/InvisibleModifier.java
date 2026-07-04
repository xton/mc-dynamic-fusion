package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: hide the shot's flight trail, overriding the weapon-type
 * default (a bow shot renders by default). Complement of {@link VisibleModifier}
 * — for a stealthy, invisible bolt.
 */
public final class InvisibleModifier implements Modifier {

    public static final String ID = "INVISIBLE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Invisible";
    }

    @Override
    public String description() {
        return "hides its flight trail";
    }

    @Override
    public String detailedDescription() {
        return "Suppresses the shot's trail, so it flies unseen — the opposite of Visible.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().setVisibleTrail(false);
        builder.projectile().setHideTrail(true);
    }
}
