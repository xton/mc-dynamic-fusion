package com.xton.fusion.modifier;

/**
 * One area-of-effect element in a projectile's payload — a concrete emitter
 * (PUSH or DAMAGE burst) that transforms then scale or decorate.
 *
 * <p>It is a mutable compile-time value: an emitter modifier creates one with
 * base radius/power, and the transforms that follow it in the stack (EXPAND,
 * AMPLIFY, CHAIN, INVERT, PERSIST) mutate <em>this</em> element — the nearest
 * preceding one. Read at fire time by {@code AoeBurst}.
 */
public final class AoeSpec {

    private final AoeKind kind;
    private double radius;
    private double power;        // PUSH: knockback strength · DAMAGE: half-hearts
    private int chainCount;      // extra entities to hop to after the main burst
    private boolean inverted;    // PUSH only: pull inward instead of out
    private int persistTicks;    // re-pulse the burst for this long

    public AoeSpec(AoeKind kind, double radius, double power) {
        this.kind = kind;
        this.radius = radius;
        this.power = power;
    }

    public AoeKind kind() {
        return kind;
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

    /** AMPLIFY: multiply the power/damage (×N per apply). */
    public void scalePower(double factor) {
        this.power *= factor;
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
