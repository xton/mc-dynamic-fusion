package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.TrailStyle;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Flight transform: force the shot to render its flight trail, overriding the
 * weapon-type default (a melee poke is invisible by default). Handy on a
 * long-range melee build so you can actually see the bolt travel. Complement of
 * {@link InvisibleModifier}.
 */
public final class VisibleModifier implements Modifier {

    public static final String ID = "VISIBLE";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Visible";
    }

    @Override
    public String description() {
        return "shows a flight trail";
    }

    @Override
    public String detailedDescription() {
        return "Renders the shot's trail even on weapons that fire invisibly (like a melee swing), so you can see it fly.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        builder.projectile().setTrailStyle(TrailStyle.BRIGHT);
    }
}
