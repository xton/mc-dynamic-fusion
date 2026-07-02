package com.xton.fusion.modifier;

import java.util.ArrayList;
import java.util.List;

/**
 * A compiled projectile: its <b>flight</b> (how it travels) plus its
 * <b>payload</b> (the ordered {@link AoeSpec} elements it delivers where it
 * terminates). This is what the modifier stack compiles down to and what the
 * launcher/projectile read — the flat per-modifier context is gone.
 *
 * <p>Flight transforms (PIERCE, LIFETIME, SPREAD, MULTISHOT, MINING, gravity)
 * mutate the flight fields here; emitter modifiers append an {@link AoeSpec} to
 * {@link #payload}; AOE transforms mutate the {@link #topAoe() top} element.
 */
public final class ProjectileSpec {

    // ----- flight -----
    private int count = 1;             // MULTISHOT: projectiles launched
    private double spreadDegrees;      // SPREAD: random aim cone half-angle
    private double speed;              // launch speed, blocks/tick
    private boolean gravity;           // gravity seam: arc vs. straight bolt
    private boolean pierce;            // pass through soft blocks/entities
    private double pierceMaxHardness;  // blocks harder than this stop a piercing shot
    private int bounces;               // bounce seam
    private int lifetimeTicks;         // ticks before it expires & terminates
    private boolean mining;            // breaks soft blocks along the flight

    // ----- payload -----
    private final List<AoeSpec> payload = new ArrayList<>();

    // ----- flight accessors -----

    public int count() {
        return count;
    }

    public void addCount(int extra) {
        this.count += extra;
    }

    public double spreadDegrees() {
        return spreadDegrees;
    }

    public void addSpreadDegrees(double degrees) {
        this.spreadDegrees += degrees;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public boolean hasGravity() {
        return gravity;
    }

    public void setGravity(boolean gravity) {
        this.gravity = gravity;
    }

    public boolean isPierce() {
        return pierce;
    }

    public void setPierce(boolean pierce) {
        this.pierce = pierce;
    }

    public double pierceMaxHardness() {
        return pierceMaxHardness;
    }

    public void setPierceMaxHardness(double maxHardness) {
        this.pierceMaxHardness = maxHardness;
    }

    public int bounces() {
        return bounces;
    }

    public void addBounces(int count) {
        this.bounces += count;
    }

    public int lifetimeTicks() {
        return lifetimeTicks;
    }

    public void setLifetimeTicks(int ticks) {
        this.lifetimeTicks = ticks;
    }

    public void addLifetimeTicks(int ticks) {
        this.lifetimeTicks += ticks;
    }

    public boolean isMining() {
        return mining;
    }

    public void setMining(boolean mining) {
        this.mining = mining;
    }

    // ----- payload accessors -----

    public List<AoeSpec> payload() {
        return payload;
    }

    /** Append an emitter to the payload and return it (now the top element). */
    public AoeSpec addAoe(AoeSpec spec) {
        payload.add(spec);
        return spec;
    }

    /** The nearest preceding AOE element (the last one added), or null if none. */
    public AoeSpec topAoe() {
        return payload.isEmpty() ? null : payload.get(payload.size() - 1);
    }
}
