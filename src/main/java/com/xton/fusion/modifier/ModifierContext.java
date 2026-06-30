package com.xton.fusion.modifier;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Mutable state carried through the modifier pipeline.
 *
 * <p>The Bukkit references ({@link #caster}, {@link #origin}) are optional and
 * are populated by the weapon behaviour layer. Pure modifiers like
 * {@code NOVA} only read/write the primitive effect parameters, so they can be
 * unit-tested by constructing a context with no caster.
 */
public class ModifierContext {

    // Input — populated by the behaviour layer, nullable in pure tests.
    private Player caster;
    private Location origin;

    // Effect parameters — what the modifiers compute.
    private double radius;
    private double power;
    private double amplifier = 1.0; // running potency multiplier (AMPLIFY compounds this)
    private boolean radial;

    public Player getCaster() {
        return caster;
    }

    public ModifierContext setCaster(Player caster) {
        this.caster = caster;
        return this;
    }

    public Location getOrigin() {
        return origin;
    }

    public ModifierContext setOrigin(Location origin) {
        this.origin = origin;
        return this;
    }

    public double getRadius() {
        return radius;
    }

    public ModifierContext setRadius(double radius) {
        this.radius = radius;
        return this;
    }

    public double getPower() {
        return power;
    }

    public ModifierContext setPower(double power) {
        this.power = power;
        return this;
    }

    public double getAmplifier() {
        return amplifier;
    }

    public ModifierContext setAmplifier(double amplifier) {
        this.amplifier = amplifier;
        return this;
    }

    public boolean isRadial() {
        return radial;
    }

    public ModifierContext setRadial(boolean radial) {
        this.radial = radial;
        return this;
    }
}
