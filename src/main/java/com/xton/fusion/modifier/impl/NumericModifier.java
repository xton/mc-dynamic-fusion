package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ParameterizedModifier;
import com.xton.fusion.modifier.WeaponBuilder;
import com.xton.fusion.util.Format;

/**
 * Base for modifiers whose ID carries one numeric parameter after a colon —
 * {@code SPEED:0.8}, {@code DURATION:3}, {@code DELAY:2}. The registry holds one
 * bare template (value {@code null}); {@link #withParameter} parses and clamps
 * the number and mints the bound instance via {@link #bind}. Subclasses declare
 * their bounds and what the value does; parsing, clamping, and the canonical
 * {@code BASE:value} ID (stable across PDC round-trips) live here.
 */
abstract class NumericModifier implements ParameterizedModifier {

    private final String baseId;
    private final double min;
    private final double max;
    /** The bound value, or null for the bare (inert) registry template. */
    private final Double value;

    protected NumericModifier(String baseId, double min, double max, Double value) {
        this.baseId = baseId;
        this.min = min;
        this.max = max;
        this.value = value;
    }

    /** Mint the concrete instance bound to a parsed, clamped {@code value}. */
    protected abstract Modifier bind(double value);

    /** Act on the compile state with the bound value. */
    protected abstract void apply(WeaponBuilder builder, double value);

    /** Display suffix after the number (e.g. {@code "s"} for seconds). */
    protected String unitSuffix() {
        return "";
    }

    @Override
    public final Modifier withParameter(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        try {
            return bind(Math.clamp(Double.parseDouble(param.trim()), min, max));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public final String id() {
        return value == null ? baseId : baseId + ":" + Format.number(value);
    }

    @Override
    public final String displayName() {
        String base = Format.prettyName(baseId);
        return value == null ? base : base + " " + Format.number(value) + unitSuffix();
    }

    @Override
    public final void apply(WeaponBuilder builder) {
        if (value != null) {
            apply(builder, value);
        }
    }
}
