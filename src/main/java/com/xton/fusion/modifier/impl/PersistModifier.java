package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Transform: leaves the nearest preceding burst lingering, re-pulsing on a timer
 * for a while. Stacks: each copy adds {@code ticksPerApply} of duration. Inert
 * without an emitter before it.
 */
public final class PersistModifier implements Modifier {

    public static final String ID = "PERSIST";

    private final int ticksPerApply;

    public PersistModifier(int ticksPerApply) {
        this.ticksPerApply = ticksPerApply;
    }

    public PersistModifier() {
        this(60);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Persist";
    }

    @Override
    public String description() {
        return "burst lingers and re-pulses";
    }

    @Override
    public String detailedDescription() {
        return "Drops a zone at the burst point that keeps re-firing the effect. Stacks for a longer-lasting field.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        AoeSpec target = builder.topAoe();
        if (target != null) {
            target.addPersist(ticksPerApply);
        }
    }
}
