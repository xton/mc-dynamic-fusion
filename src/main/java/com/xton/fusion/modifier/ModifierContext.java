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
    private double expandBonus;     // EXPAND adds to the effect radius (stacks)
    private int chainCount;         // CHAIN: extra entities to hop to (stacks)
    private int repeatCount;        // REPEAT: extra times the effect fires (stacks)
    private int delayTicks;         // DELAYED: ticks before the effect fires (stacks)
    private boolean mining;         // MINING: weapon carves blocks ahead on swing

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

    public double getExpandBonus() {
        return expandBonus;
    }

    public ModifierContext addExpandBonus(double bonus) {
        this.expandBonus += bonus;
        return this;
    }

    public int getChainCount() {
        return chainCount;
    }

    public ModifierContext addChainCount(int count) {
        this.chainCount += count;
        return this;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public ModifierContext addRepeatCount(int count) {
        this.repeatCount += count;
        return this;
    }

    public int getDelayTicks() {
        return delayTicks;
    }

    public ModifierContext addDelayTicks(int ticks) {
        this.delayTicks += ticks;
        return this;
    }

    public boolean isMining() {
        return mining;
    }

    public ModifierContext setMining(boolean mining) {
        this.mining = mining;
        return this;
    }
}
