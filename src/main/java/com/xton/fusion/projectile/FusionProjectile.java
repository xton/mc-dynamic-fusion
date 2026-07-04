package com.xton.fusion.projectile;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.ProjectileSpec;

/**
 * A custom, particle-rendered projectile ticked entirely by us — no Bukkit
 * entity, so we own its physics. Each tick it sub-steps along its velocity and,
 * at each point, applies its AOEs according to three orthogonal triggers:
 *
 * <ul>
 *   <li><b>terminus</b> (always) — where it finally stops or expires;</li>
 *   <li><b>PIERCE</b> — at every <em>occupied</em> cell (a block, or an entity it
 *       contacts) it passes, instead of stopping there;</li>
 *   <li><b>TRAIL</b> — at every <em>empty</em> cell it passes.</li>
 * </ul>
 *
 * <p>Entity bursts (PUSH/DAMAGE) fire at the terminus and at each pierced
 * entity; the environmental kinds (MINING/FIRE/ICE/DEPOSIT) sweep the blocks in
 * radius (and burn/freeze mobs) at their trigger cells. At the terminus it also
 * launches any SPAWN children — off the <em>reflected</em> heading when it hit a
 * block, so they don't just crash back into the same surface — and, if TELEPORT,
 * moves the caster there.
 *
 * <p><b>BOUNCE</b> changes block handling: instead of terminating on a block, the
 * shot reflects off the surface (losing a little speed, and dragging its glide on
 * a floor bounce) and flies on, only triggering when it rolls to a rest, expires,
 * or hits a mob directly.
 */
public final class FusionProjectile extends BukkitRunnable {

    /** Blocks per sub-step when scanning a tick of travel. */
    private static final double STEP = 0.3;
    /** Half-extent of the entity contact box, in blocks. */
    private static final double HIT_RADIUS = 0.65;
    /** Downward acceleration per tick when gravity is on (matches a light arrow). */
    private static final double GRAVITY_PER_TICK = 0.05;
    /** Fixed nudge a piercing shot gives each entity it passes through. */
    private static final double CONTACT_IMPULSE = 0.35;
    /** Speed retained across a BOUNCE off a block (energy loss, so it settles). */
    private static final double BOUNCE_RESTITUTION = 0.82;
    /** Extra horizontal drag on a floor bounce, so a BOUNCE shot rolls to a stop. */
    private static final double BOUNCE_FLOOR_FRICTION = 0.7;
    /** Below this speed a BOUNCE shot has come to rest — it stops and triggers. */
    private static final double BOUNCE_REST_SPEED = 0.15;
    /** Blocks of flight before TRAIL starts laying its wake, so it clears the caster. */
    private static final double TRAIL_WARMUP = 2.5;
    /** How far a HOMING shot looks for a creature to chase. */
    private static final double HOMING_RANGE = 12.0;
    /** Max radians a HOMING shot turns per tick, per HOMING stack. */
    private static final double HOMING_TURN_PER_STACK = 0.13;

    private final Plugin plugin;
    private final Payload payload;
    private final ProjectileSpec spec;
    private final World world;
    private final Shot shot;
    private final Player caster;
    private final EnvironmentalAoe env;

    private final Vector position;
    private final Vector velocity;
    private final Set<UUID> contacted = new HashSet<>();
    private final Set<Long> envCells = new HashSet<>();
    private final double envEntityRadius;

    private int age;
    private double traveled; // cumulative flight distance, for the TRAIL warm-up

    public FusionProjectile(Plugin plugin, Payload payload, ProjectileSpec spec,
                            World world, Location origin, Vector velocity, Shot shot) {
        this.plugin = plugin;
        this.payload = payload;
        this.spec = spec;
        this.world = world;
        this.shot = shot;
        this.caster = shot.caster();
        this.env = new EnvironmentalAoe(world, caster, shot.env());
        this.position = origin.toVector();
        this.velocity = velocity.clone();
        this.envEntityRadius = maxEnvironmentalRadius();
    }

    /** Begin ticking this projectile on the main thread. */
    public void start() {
        runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void run() {
        if (world == null) {
            terminate(null);
            return;
        }
        if (spec.hasGravity()) {
            velocity.setY(velocity.getY() - GRAVITY_PER_TICK);
        }
        if (spec.isHoming()) {
            homeTowardTarget();
        }

        double distance = velocity.length();
        int steps = Math.max(1, (int) Math.ceil(distance / STEP));
        Vector stepVec = velocity.clone().multiply(1.0 / steps);
        double stepLen = stepVec.length();

        for (int i = 0; i < steps; i++) {
            position.add(stepVec);
            traveled += stepLen;
            Location here = position.toLocation(world);

            if (!inBounds(here)) {
                terminate(here);
                return;
            }
            trail(here);
            environmentalAlongPath(here);
            if (hitEntityStops(here, stepVec)) {
                terminate(here); // a direct mob hit triggers even a bouncing shot
                return;
            }
            if (blockStops(here)) {
                if (spec.isBounce()) {
                    bounceOff(here, stepVec);
                    return; // resume next tick on the reflected heading
                }
                terminate(here, reflectedHeading(here)); // spawns launch off the surface
                return;
            }
        }

        if (++age >= Math.max(1, spec.lifetimeTicks())) {
            terminate(position.toLocation(world)); // expired: terminate where it is
        }
    }

    /**
     * Ricochet off the block at {@code here}: back out of it, reflect the velocity
     * about the surface normal (shedding a little speed), and let the next tick
     * fly on. A floor bounce also drags the horizontal glide, so the shot slows to
     * a roll and — once it's crawling — comes to rest and triggers there. Expiry
     * is time-based (the lifetime tick cap), so pair BOUNCE with DURATION to set
     * how long it rattles around.
     */
    private void bounceOff(Location here, Vector stepVec) {
        position.subtract(stepVec); // step back to the last open cell
        Vector normal = impactNormal(here);
        double dot = velocity.dot(normal);
        velocity.subtract(normal.clone().multiply(2 * dot)); // reflect
        velocity.multiply(BOUNCE_RESTITUTION);
        if (normal.getY() > 0.5) { // bounced off a floor: rolling friction
            velocity.setX(velocity.getX() * BOUNCE_FLOOR_FRICTION);
            velocity.setZ(velocity.getZ() * BOUNCE_FLOOR_FRICTION);
        }
        if (spec.hasVisibleTrail()) {
            world.spawnParticle(Particle.CRIT, position.toLocation(world), 4, 0.1, 0.1, 0.1, 0.05);
        }
        world.playSound(here, Sound.BLOCK_STONE_HIT, 0.4f, 1.4f);
        if (velocity.length() < BOUNCE_REST_SPEED) {
            terminate(position.toLocation(world)); // rolled to a stop — go off here
            return;
        }
        if (++age >= Math.max(1, spec.lifetimeTicks())) {
            terminate(position.toLocation(world));
        }
    }

    /**
     * Steer the velocity toward the nearest creature within range, turning at most
     * a fixed angle per tick (scaled by HOMING stacks) so it curves to chase
     * rather than snapping on. Re-acquires every tick, so a dead/fled target is
     * dropped for the next-nearest.
     */
    private void homeTowardTarget() {
        LivingEntity target = nearestTarget();
        if (target == null) {
            return;
        }
        Vector desired = target.getLocation().add(0, 0.6, 0).toVector().subtract(position);
        if (desired.lengthSquared() < 1.0e-6) {
            return;
        }
        desired.normalize();
        double speed = velocity.length();
        if (speed < 1.0e-6) {
            return;
        }
        Vector cur = velocity.clone().multiply(1.0 / speed);
        double maxTurn = HOMING_TURN_PER_STACK * spec.homing();
        double dot = Math.max(-1.0, Math.min(1.0, cur.dot(desired)));
        double angle = Math.acos(dot);
        Vector dir;
        if (angle <= maxTurn || angle < 1.0e-4) {
            dir = desired; // close enough to point straight at it
        } else {
            double f = maxTurn / angle; // partial turn toward the target
            dir = cur.multiply(1 - f).add(desired.multiply(f)).normalize();
        }
        velocity.copy(dir.multiply(speed));
    }

    /** The nearest non-caster living creature within homing range, or null. */
    private LivingEntity nearestTarget() {
        Location here = position.toLocation(world);
        LivingEntity best = null;
        double bestSq = HOMING_RANGE * HOMING_RANGE;
        for (Entity entity : world.getNearbyEntities(here, HOMING_RANGE, HOMING_RANGE, HOMING_RANGE)) {
            if (!(entity instanceof LivingEntity living) || living.equals(caster)) {
                continue;
            }
            double distSq = entity.getLocation().toVector().distanceSquared(position);
            if (distSq < bestSq) {
                bestSq = distSq;
                best = living;
            }
        }
        return best;
    }

    private boolean inBounds(Location loc) {
        if (loc.getY() < world.getMinHeight() || loc.getY() > world.getMaxHeight()) {
            return false;
        }
        return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    // ----- environmental (MINING/FIRE/ICE/DEPOSIT) along the path -----

    /**
     * Apply the environmental block sweep at this cell if it's a trigger point:
     * an occupied cell while PIERCEing, or an empty cell while TRAILing. Mining
     * breaks here first, which is what lets the shot continue through it.
     *
     * <p>TRAIL has a short warm-up ({@link #TRAIL_WARMUP} blocks) so its wake
     * begins downrange — otherwise a water/lava trail floods the caster's own tile.
     */
    private void environmentalAlongPath(Location here) {
        if (!spec.hasEnvironmental()) {
            return;
        }
        boolean occupied = !here.getBlock().getType().isAir();
        boolean pierceHere = occupied && spec.isPierce();
        boolean trailHere = !occupied && spec.isTrail() && traveled >= TRAIL_WARMUP;
        if (pierceHere || trailHere) {
            applyEnvironmentalBlocks(here);
        }
    }

    /** Sweep every environmental AOE's blocks at {@code here}, once per block cell. */
    private void applyEnvironmentalBlocks(Location here) {
        if (!envCells.add(blockKey(here))) {
            return; // this cell already swept
        }
        for (AoeSpec aoe : spec.payload()) {
            if (aoe.kind().isEnvironmental()) {
                env.applyBlocks(aoe, here);
            }
        }
    }

    /** Apply the environmental mob effects (FIRE burns, ICE freezes) to one entity. */
    private void applyEnvironmentalEntity(LivingEntity target) {
        for (AoeSpec aoe : spec.payload()) {
            if (aoe.kind() == AoeKind.FIRE || aoe.kind() == AoeKind.ICE) {
                env.applyEntity(aoe, target);
            }
        }
    }

    private double maxEnvironmentalRadius() {
        double max = 0;
        for (AoeSpec aoe : spec.payload()) {
            if (aoe.kind().isEnvironmental()) {
                max = Math.max(max, aoe.radius());
            }
        }
        return max;
    }

    // ----- flight: entities and blocks -----

    /**
     * Handle entities at {@code here}. A non-piercing shot stops at the first
     * one (its terminus does the work); a piercing shot bursts + burns/freezes
     * each new entity and carries on.
     */
    private boolean hitEntityStops(Location here, Vector travel) {
        for (Entity entity : world.getNearbyEntities(here, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
            if (!(entity instanceof LivingEntity living) || living.equals(caster)) {
                continue;
            }
            if (!contacted.add(living.getUniqueId())) {
                continue; // already hit this one
            }
            if (!spec.isPierce()) {
                return true; // stop at the first entity; the terminus acts here
            }
            payload.detonate(world, here, caster, shot.generation());
            applyEnvironmentalEntity(living);
            contactShove(living, travel);
        }
        return false;
    }

    /** True if the (possibly just-mined) block here stops the shot. */
    private boolean blockStops(Location here) {
        Material type = here.getBlock().getType();
        if (!type.isSolid()) {
            return false; // air, grass, fluids — no collision
        }
        if (spec.isPierce() && isBreakable(type)) {
            return false; // ghost through a soft block
        }
        return true; // solid and (too hard, or not piercing)
    }

    private boolean isBreakable(Material type) {
        double hardness = type.getHardness();
        return hardness >= 0 && hardness <= spec.pierceMaxHardness();
    }

    /**
     * The outward normal of the block face the shot entered — the axis it
     * approached most strongly whose neighbour that way is open, so we reflect
     * (and spawn) back into free air rather than into another solid.
     */
    private Vector impactNormal(Location impact) {
        Block hit = impact.getBlock();
        Vector[] faces = {
            new Vector(-Math.signum(velocity.getX()), 0, 0),
            new Vector(0, -Math.signum(velocity.getY()), 0),
            new Vector(0, 0, -Math.signum(velocity.getZ())),
        };
        double[] mag = {Math.abs(velocity.getX()), Math.abs(velocity.getY()), Math.abs(velocity.getZ())};
        Integer[] order = {0, 1, 2};
        java.util.Arrays.sort(order, (a, b) -> Double.compare(mag[b], mag[a]));
        Vector fallback = null;
        for (int idx : order) {
            if (mag[idx] < 1.0e-9) {
                continue; // no approach along this axis
            }
            Vector n = faces[idx];
            if (fallback == null) {
                fallback = n;
            }
            if (!hit.getRelative(n.getBlockX(), n.getBlockY(), n.getBlockZ()).getType().isSolid()) {
                return n; // this face opens onto air — bounce out here
            }
        }
        return fallback != null ? fallback : new Vector(0, 1, 0);
    }

    /** The direction SPAWN children take off a block terminus: velocity reflected off the surface. */
    private Vector reflectedHeading(Location impact) {
        Vector normal = impactNormal(impact);
        Vector reflected = velocity.clone().subtract(normal.clone().multiply(2 * velocity.dot(normal)));
        return reflected.lengthSquared() > 1.0e-6 ? reflected.normalize() : normal;
    }

    /** The plain forward heading (for non-block terminations): velocity, or up if stalled. */
    private Vector forwardHeading() {
        return velocity.lengthSquared() > 1.0e-6 ? velocity.clone().normalize() : new Vector(0, 1, 0);
    }

    private void contactShove(LivingEntity target, Vector travel) {
        Vector push = travel.clone();
        if (push.lengthSquared() < 1.0e-6) {
            return;
        }
        push.normalize().multiply(CONTACT_IMPULSE);
        push.setY(Math.max(push.getY(), 0.2));
        target.setVelocity(target.getVelocity().add(push));
    }

    private void trail(Location here) {
        if (spec.isTrailHidden()) {
            return; // INVISIBLE — a truly unseen bolt
        }
        if (spec.hasVisibleTrail()) {
            // Ranged shots render a bright crit trail (+ mining sparks).
            world.spawnParticle(Particle.CRIT, here, 1, 0.02, 0.02, 0.02, 0.0);
            if (spec.isMining()) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, here, 1, 0.02, 0.02, 0.02, 0.0);
            }
            return;
        }
        // A melee swing throws a subtle "energy ball" — visible enough to read on a
        // long-range build, faint enough that a near-instant poke still looks like a
        // swing. Much softer than the ranged trail or a burst.
        world.spawnParticle(Particle.ENCHANTED_HIT, here, 1, 0.0, 0.0, 0.0, 0.0);
    }

    // ----- terminus -----

    /**
     * The shot ends: fire the entity burst and environmental sweep here, burn/
     * freeze nearby mobs, launch any SPAWN children, and TELEPORT the caster.
     */
    private void terminate(Location where) {
        terminate(where, forwardHeading());
    }

    private void terminate(Location where, Vector childHeading) {
        try {
            if (where != null && world != null) {
                payload.detonate(world, where, caster, shot.generation());
                if (spec.hasEnvironmental()) {
                    applyEnvironmentalBlocks(where);
                    for (Entity entity : world.getNearbyEntities(where,
                            envEntityRadius, envEntityRadius, envEntityRadius)) {
                        if (entity instanceof LivingEntity living && !living.equals(caster)) {
                            applyEnvironmentalEntity(living);
                        }
                    }
                }
                spawnChildren(where, childHeading);
                maybeTeleport(where);
            }
        } finally {
            cancel();
        }
    }

    private void spawnChildren(Location where, Vector heading) {
        if (spec.spawns().isEmpty() || !shot.canSpawn()) {
            return;
        }
        Vector h = heading.lengthSquared() > 1.0e-6 ? heading.clone().normalize() : new Vector(0, 1, 0);
        shot.launcher().spawnChildren(spec.spawns(), where, h, shot);
    }

    private void maybeTeleport(Location where) {
        if (!spec.isTeleport() || caster == null) {
            return;
        }
        AtomicBoolean latch = shot.teleportLatch();
        if (latch != null && !latch.compareAndSet(false, true)) {
            return; // this cast already teleported
        }
        Location safe = safeLanding(where);
        if (safe == null) {
            return; // nowhere safe within reach — better to stay put than suffocate
        }
        safe.setDirection(caster.getLocation().getDirection());
        caster.teleport(safe);
        world.spawnParticle(Particle.PORTAL, safe.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
        world.playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
    }

    /** Back off along the reverse flight until the caster fits (feet+head clear, no mob). */
    private Location safeLanding(Location terminus) {
        Vector step = velocity.lengthSquared() > 1.0e-6
                ? velocity.clone().normalize() : new Vector(0, 1, 0);
        Location cand = terminus.clone();
        for (int i = 0; i < 6; i++) {
            if (cand.getBlock().isPassable()
                    && cand.clone().add(0, 1, 0).getBlock().isPassable()
                    && !mobAt(cand)) {
                return cand;
            }
            cand.subtract(step);
        }
        return null;
    }

    private boolean mobAt(Location loc) {
        for (Entity e : world.getNearbyEntities(loc, 0.5, 1.0, 0.5)) {
            if (e instanceof LivingEntity living && !living.equals(caster)) {
                return true;
            }
        }
        return false;
    }

    private static long blockKey(Location loc) {
        return ((long) loc.getBlockX() & 0x3FFFFFFL) << 38
                | ((long) loc.getBlockZ() & 0x3FFFFFFL) << 12
                | ((long) loc.getBlockY() & 0xFFFL);
    }

    /** Current (or, once stopped, final) Y — used by the self-test to observe gravity. */
    public double positionY() {
        return position.getY();
    }
}
