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
    private boolean visibleTrail = true; // render the flight trail (off for a short melee poke)
    private boolean trail;             // TRAIL: apply AOEs at every empty-air cell too
    private boolean teleport;          // TELEPORT: move the caster to the terminus

    // ----- payload -----
    private final List<AoeSpec> payload = new ArrayList<>();
    // ----- child projectiles spawned at the terminus (SPAWN / cluster) -----
    private final List<ProjectileSpec> spawns = new ArrayList<>();

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

    /** The MINING emitter in the payload (the tunnel-carver), or null if none. */
    public AoeSpec miningAoe() {
        for (AoeSpec aoe : payload) {
            if (aoe.kind() == AoeKind.MINING) {
                return aoe;
            }
        }
        return null;
    }

    /** True when the stack carries a MINING emitter (derived from the payload). */
    public boolean isMining() {
        return miningAoe() != null;
    }

    public boolean hasVisibleTrail() {
        return visibleTrail;
    }

    public void setVisibleTrail(boolean visibleTrail) {
        this.visibleTrail = visibleTrail;
    }

    public boolean isTrail() {
        return trail;
    }

    public void setTrail(boolean trail) {
        this.trail = trail;
    }

    public boolean isTeleport() {
        return teleport;
    }

    public void setTeleport(boolean teleport) {
        this.teleport = teleport;
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

    /** True when any environmental AOE (MINING/FIRE/ICE/DEPOSIT) is present. */
    public boolean hasEnvironmental() {
        for (AoeSpec aoe : payload) {
            if (aoe.kind().isEnvironmental()) {
                return true;
            }
        }
        return false;
    }

    // ----- child projectiles (SPAWN) -----

    public List<ProjectileSpec> spawns() {
        return spawns;
    }

    /** Register a fresh child projectile to launch at this one's terminus. */
    public void addSpawn(ProjectileSpec child) {
        spawns.add(child);
    }
}
