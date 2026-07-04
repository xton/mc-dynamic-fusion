package com.xton.fusion.modifier;

import org.bukkit.Material;

/**
 * One area-of-effect element in a projectile's payload — a concrete emitter that
 * the transforms after it scale or decorate.
 *
 * <p>It is a mutable compile-time value: an emitter modifier creates one with
 * base radius/power, and the transforms that follow it in the stack (EXPAND,
 * AMPLIFY, CHAIN, INVERT, PERSIST) mutate <em>this</em> element — the nearest
 * preceding one. Read at fire time by the burst / environmental applicators.
 */
public final class AoeSpec {

    private final AoeKind kind;
    private double radius;
    private double power;        // PUSH: knockback strength · DAMAGE: half-hearts
    private int chainCount;      // extra entities to hop to after the main burst
    private boolean inverted;    // PUSH only: pull inward instead of out
    private int persistTicks;    // re-pulse the burst for this long
    private final Material material; // DEPOSIT: the block to place; null otherwise

    public AoeSpec(AoeKind kind, double radius, double power) {
        this(kind, radius, power, null);
    }

    public AoeSpec(AoeKind kind, double radius, double power, Material material) {
        this.kind = kind;
        this.radius = radius;
        this.power = power;
        this.material = material;
    }

    public AoeKind kind() {
        return kind;
    }

    /** DEPOSIT: the block to place in the radius; null for other kinds. */
    public Material material() {
        return material;
    }

    public double radius() {
        return radius;
    }

    public double power() {
        return power;
    }

    public int chainCount() {
        return chainCount;
    }

    public boolean inverted() {
        return inverted;
    }

    public int persistTicks() {
        return persistTicks;
    }

    // ----- transforms (applied by the modifiers that follow this emitter) -----

    /** EXPAND: multiply the radius (×N per apply). */
    public void scaleRadius(double factor) {
        this.radius *= factor;
    }

    /** AMPLIFY: multiply the power/damage (×N per apply). For MINING this scales its break hardness. */
    public void scalePower(double factor) {
        this.power *= factor;
    }

    /** Stack additional power onto this element (MINING: raise the break-hardness cap per extra MINING). */
    public void addPower(double delta) {
        this.power += delta;
    }

    /** CHAIN: add hops to further entities. */
    public void addChain(int count) {
        this.chainCount += count;
    }

    /** INVERT: flip a PUSH inward; two cancel. */
    public void toggleInvert() {
        this.inverted = !this.inverted;
    }

    /** PERSIST: extend how long the burst re-pulses. */
    public void addPersist(int ticks) {
        this.persistTicks += ticks;
    }
}
