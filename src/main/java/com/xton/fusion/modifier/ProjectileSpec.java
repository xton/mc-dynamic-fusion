package com.xton.fusion.modifier;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

/**
 * A compiled projectile: its <b>flight</b> (how it travels) plus its
 * <b>payload</b> (the ordered {@link AoeSpec} elements it delivers where it
 * terminates). This is what the modifier stack compiles down to and what the
 * launcher/projectile read.
 *
 * <p>Flight transforms (PIERCE, BOUNCE, HOMING, TRAIL, TELEPORT, MULTISHOT,
 * SPREAD, LIFETIME, GRAVITY, VISIBLE/INVISIBLE, SPEED:n, DURATION:n) mutate the
 * flight fields here; emitter modifiers append an {@link AoeSpec} to
 * {@link #payload} (or set the mob type / register a SPAWN child); AOE
 * transforms mutate the {@link #topAoe() top} element.
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
    private TrailStyle trailStyle = TrailStyle.BRIGHT; // how the flight wake renders
    private boolean trail;             // TRAIL: apply AOEs at every empty-air cell too
    private boolean teleport;          // TELEPORT: move the caster to the terminus
    private int homing;                // HOMING: steer toward a nearby target (stacks = sharper turn)
    private int treasure;              // TREASURE (Golden Brush): loot level — more gold = more/rarer drops

    private int spawnDelayTicks;       // DELAY: ticks to wait before launching this child
    private EntityType mobType;        // MOB:<type>: launch this living entity as the projectile
    private int auraRateTicks;         // RATE: ticks between worn-armor aura pulses (0 = use the config default)

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

    /**
     * True when the shot ricochets off blocks instead of terminating on them
     * (BOUNCE) — it then only triggers at expiry or on a direct mob hit.
     */
    public boolean isBounce() {
        return bounces > 0;
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

    /** The DETECT sensor in the payload (the armed trigger radius), or null if none. */
    public AoeSpec detectAoe() {
        for (AoeSpec aoe : payload) {
            if (aoe.kind() == AoeKind.DETECT) {
                return aoe;
            }
        }
        return null;
    }

    /** True when this spec is an armed DETECT child (derived from the payload). */
    public boolean isDetect() {
        return detectAoe() != null;
    }

    /** How the flight wake renders — seeded by weapon type, overridden by VISIBLE/INVISIBLE. */
    public TrailStyle trailStyle() {
        return trailStyle;
    }

    public void setTrailStyle(TrailStyle trailStyle) {
        this.trailStyle = trailStyle;
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

    /** HOMING strength — how many HOMING modifiers were applied (0 = none); more = sharper turn. */
    public int homing() {
        return homing;
    }

    public void addHoming(int count) {
        this.homing += count;
    }

    public boolean isHoming() {
        return homing > 0;
    }

    /** TREASURE (Golden Brush) loot level — how many TREASURE modifiers were applied (0 = none). */
    public int treasure() {
        return treasure;
    }

    public void addTreasure(int count) {
        this.treasure += count;
    }

    /** The POTION emitter in the payload (the Wand's cast area), or null if none. */
    public AoeSpec potionAoe() {
        for (AoeSpec aoe : payload) {
            if (aoe.kind() == AoeKind.POTION) {
                return aoe;
            }
        }
        return null;
    }

    /** POTION:&lt;type&gt; (Wand): the effect this fused item casts, or null if it isn't a Wand. */
    public PotionEffectType potionType() {
        AoeSpec aoe = potionAoe();
        return aoe == null ? null : aoe.potionEffectType();
    }

    /** POTION (Wand): the cast radius (widened by EXPAND like any other burst), or 0 if it isn't a Wand. */
    public double potionRadius() {
        AoeSpec aoe = potionAoe();
        return aoe == null ? 0 : aoe.radius();
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

    /** DELAY: ticks to wait after the parent's terminus before launching this child (0 = at once). */
    public int spawnDelayTicks() {
        return spawnDelayTicks;
    }

    public void setSpawnDelayTicks(int ticks) {
        this.spawnDelayTicks = ticks;
    }

    /** MOB:&lt;type&gt;: the living entity to launch as the projectile, or null for a normal bolt. */
    public EntityType mobType() {
        return mobType;
    }

    public void setMobType(EntityType mobType) {
        this.mobType = mobType;
    }

    /** RATE (worn armor only): ticks between aura pulses, or 0 to fall back to the config default. */
    public int auraRateTicks() {
        return auraRateTicks;
    }

    public void setAuraRateTicks(int ticks) {
        this.auraRateTicks = ticks;
    }
}
