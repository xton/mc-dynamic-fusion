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
 * launches any SPAWN children and, if TELEPORT, moves the caster there.
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

        double distance = velocity.length();
        int steps = Math.max(1, (int) Math.ceil(distance / STEP));
        Vector stepVec = velocity.clone().multiply(1.0 / steps);

        for (int i = 0; i < steps; i++) {
            position.add(stepVec);
            Location here = position.toLocation(world);

            if (!inBounds(here)) {
                terminate(here);
                return;
            }
            trail(here);
            environmentalAlongPath(here);
            if (hitEntityStops(here, stepVec)) {
                terminate(here);
                return;
            }
            if (blockStops(here)) {
                terminate(here);
                return;
            }
        }

        if (++age >= Math.max(1, spec.lifetimeTicks())) {
            terminate(position.toLocation(world)); // expired: terminate where it is
        }
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
     */
    private void environmentalAlongPath(Location here) {
        if (!spec.hasEnvironmental()) {
            return;
        }
        boolean occupied = !here.getBlock().getType().isAir();
        if ((occupied && spec.isPierce()) || (!occupied && spec.isTrail())) {
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
        // A short melee poke keeps no visible trail — not even mining sparks — so
        // it reads as an instant swing. Ranged shots render theirs.
        if (!spec.hasVisibleTrail()) {
            return;
        }
        world.spawnParticle(Particle.CRIT, here, 1, 0.02, 0.02, 0.02, 0.0);
        if (spec.isMining()) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, here, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // ----- terminus -----

    /**
     * The shot ends: fire the entity burst and environmental sweep here, burn/
     * freeze nearby mobs, launch any SPAWN children, and TELEPORT the caster.
     */
    private void terminate(Location where) {
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
                spawnChildren(where);
                maybeTeleport(where);
            }
        } finally {
            cancel();
        }
    }

    private void spawnChildren(Location where) {
        if (spec.spawns().isEmpty() || !shot.canSpawn()) {
            return;
        }
        Vector heading = velocity.lengthSquared() > 1.0e-6
                ? velocity.clone().normalize() : new Vector(0, 1, 0);
        shot.launcher().spawnChildren(spec.spawns(), where, heading, shot);
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
