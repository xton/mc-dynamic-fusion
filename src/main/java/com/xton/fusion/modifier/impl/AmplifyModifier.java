package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Transform: multiplies the power of the nearest preceding burst (×N per copy)
 * — knockback force for a PUSH, damage for a DAMAGE. Inert on its own; it needs
 * an emitter before it to amplify.
 */
public final class AmplifyModifier implements Modifier {

    public static final String ID = "AMPLIFY";

    private final double factorPerApply;

    public AmplifyModifier(double factorPerApply) {
        this.factorPerApply = factorPerApply;
    }

    public AmplifyModifier() {
        this(1.6);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Amplify";
    }

    @Override
    public String description() {
        return "strengthens the previous burst";
    }

    @Override
    public String detailedDescription() {
        return "Multiplies the force (or damage) of the burst before it. Stacks — we allow OP.";
    }

    @Override
    public Category category() {
        return Category.TRANSFORM;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        AoeSpec target = builder.topAoe();
        if (target != null) {
            target.scalePower(factorPerApply);
        }
    }
}
