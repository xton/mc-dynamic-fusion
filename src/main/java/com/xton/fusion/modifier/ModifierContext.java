package com.xton.fusion.modifier;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Mutable state carried through the modifier pipeline.
 *
 * <p>It describes, in engine-neutral primitives, the <b>projectile(s)</b> a
 * weapon launches and the <b>burst</b> that fires where a projectile triggers —
 * modelled after Noita, where everything is a projectile plus an area action.
 * A swing (or bow shot) launches {@code count} projectiles; each flies with the
 * spec here (speed / spread / pierce / lifetime …) and, when it stops or
 * expires, fires the area effect (radius / power / chain / persist …).
 *
 * <p>The Bukkit references ({@link #caster}, {@link #origin}) are optional and
 * are populated by the weapon behaviour layer. Pure modifiers only read/write
 * the primitive fields, so they can be unit-tested with no server.
 */
public class ModifierContext {

    // Input — populated by the behaviour layer, nullable in pure tests.
    private Player caster;
    private Location origin;

    // ----- projectile spec (how the shot flies) -----
    private int count = 1;             // MULTISHOT: number of projectiles launched (stacks)
    private double spreadDegrees;      // SPREAD: half-angle of the random aim cone (stacks)
    private double speed;              // launch speed in blocks/tick (launcher sets a base)
    private boolean gravity;           // has-gravity seam: arc & fall vs. straight bolt
    private boolean pierce;            // PIERCE: pass through soft blocks/entities instead of stopping
    private double pierceMaxHardness;  // blocks harder than this stop even a piercing shot
    private int bounces;               // bounce seam: rebounds off hard surfaces (stacks)
    private int lifetimeTicks;         // LIFETIME: ticks before the shot expires & triggers (stacks)

    // ----- burst spec (what happens where the shot triggers) -----
    private double radius;
    private double power;
    private double amplifier = 1.0; // running potency multiplier (AMPLIFY compounds this)
    private boolean radial;
    private double expandBonus;     // EXPAND adds to the burst radius (stacks)
    private int chainCount;         // CHAIN: extra entities to hop to (stacks)
    private boolean mining;         // MINING: the shot breaks soft blocks it passes through
    private boolean inverted;       // INVERT: pull inward instead of shoving out (toggles)
    private int persistTicks;       // PERSIST: lingering field duration in ticks (stacks)

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

    // ----- projectile spec -----

    public int getCount() {
        return count;
    }

    /** MULTISHOT adds projectiles; base is 1. */
    public ModifierContext addCount(int extra) {
        this.count += extra;
        return this;
    }

    public double getSpreadDegrees() {
        return spreadDegrees;
    }

    public ModifierContext addSpreadDegrees(double degrees) {
        this.spreadDegrees += degrees;
        return this;
    }

    public double getSpeed() {
        return speed;
    }

    public ModifierContext setSpeed(double speed) {
        this.speed = speed;
        return this;
    }

    public boolean hasGravity() {
        return gravity;
    }

    public ModifierContext setGravity(boolean gravity) {
        this.gravity = gravity;
        return this;
    }

    public boolean isPierce() {
        return pierce;
    }

    public ModifierContext setPierce(boolean pierce) {
        this.pierce = pierce;
        return this;
    }

    public double getPierceMaxHardness() {
        return pierceMaxHardness;
    }

    public ModifierContext setPierceMaxHardness(double maxHardness) {
        this.pierceMaxHardness = maxHardness;
        return this;
    }

    public int getBounces() {
        return bounces;
    }

    public ModifierContext addBounces(int count) {
        this.bounces += count;
        return this;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public ModifierContext setLifetimeTicks(int ticks) {
        this.lifetimeTicks = ticks;
        return this;
    }

    public ModifierContext addLifetimeTicks(int ticks) {
        this.lifetimeTicks += ticks;
        return this;
    }

    // ----- burst spec -----

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

    public boolean isMining() {
        return mining;
    }

    public ModifierContext setMining(boolean mining) {
        this.mining = mining;
        return this;
    }

    public boolean isInverted() {
        return inverted;
    }

    /** INVERT flips the effect; two INVERTs cancel back to normal. */
    public ModifierContext toggleInverted() {
        this.inverted = !this.inverted;
        return this;
    }

    public int getPersistTicks() {
        return persistTicks;
    }

    public ModifierContext addPersistTicks(int ticks) {
        this.persistTicks += ticks;
        return this;
    }
}
