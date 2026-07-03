package com.xton.fusion.projectile;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.ProjectileSpec;

/**
 * A custom, particle-rendered projectile ticked entirely by us — no Bukkit
 * entity, so we own its physics. Each tick it sub-steps along its velocity,
 * checking blocks and entities along the way, which is what makes the Noita
 * primitives possible:
 *
 * <ul>
 *   <li><b>pierce</b> — passes through soft blocks and hits every entity in its
 *       path, stopping only at a block harder than the pierce cap;</li>
 *   <li><b>mining</b> — breaks the soft blocks it pierces (bores a tunnel);</li>
 *   <li><b>lifetime</b> — after so many ticks it expires and triggers wherever
 *       it is (LIFETIME lengthens this);</li>
 *   <li><b>gravity</b> / <b>bounces</b> — seams wired to fields, ready to grow.</li>
 * </ul>
 *
 * <p>When it stops (hits a hard block, hits an entity without pierce, or
 * expires) it delivers its {@link Payload} at that point. The payload may be
 * empty — a mining ray does its work along the flight and delivers nothing at
 * the terminus (no pop). A non-piercing bolt with a burst payload "triggers
 * where it lands"; a piercing shot contact-hits along the line and bursts once
 * at the end.
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
    /** Cap on the mining bore radius, so a heavily-EXPANDed ray can't level a region. */
    private static final double MINING_MAX_RADIUS = 6.0;

    private final Plugin plugin;
    private final Payload payload;
    private final ProjectileSpec spec;
    private final World world;
    private final Player caster;
    private final int generation;

    private final Vector position;
    private final Vector velocity;
    private final Set<UUID> contacted = new HashSet<>();
    private final Set<Long> minedCells = new HashSet<>();

    private int age;

    public FusionProjectile(Plugin plugin, Payload payload, ProjectileSpec spec,
                            World world, Location origin, Vector velocity,
                            Player caster, int generation) {
        this.plugin = plugin;
        this.payload = payload;
        this.spec = spec;
        this.world = world;
        this.position = origin.toVector();
        this.velocity = velocity.clone();
        this.caster = caster;
        this.generation = generation;
    }

    /** Begin ticking this projectile on the main thread. */
    public void start() {
        runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void run() {
        if (world == null) {
            stop(null);
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
                stop(here);
                return;
            }
            trail(here);

            if (hitBlockStops(here)) {
                stop(here);
                return;
            }
            if (hitEntityStops(here, stepVec)) {
                stop(here);
                return;
            }
        }

        if (++age >= Math.max(1, spec.lifetimeTicks())) {
            stop(position.toLocation(world)); // expired: terminate where it is
        }
    }

    private boolean inBounds(Location loc) {
        if (loc.getY() < world.getMinHeight() || loc.getY() > world.getMaxHeight()) {
            return false;
        }
        return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    /**
     * Handle the block at {@code here}. Returns true if the projectile should
     * stop here (a block too hard to pass); false if it may continue (open air,
     * a soft block it pierces, or a block it mined through).
     */
    private boolean hitBlockStops(Location here) {
        Block block = here.getBlock();
        Material type = block.getType();
        if (type.isAir()) {
            return false; // nothing here
        }
        boolean solid = type.isSolid();
        boolean breakable = isBreakable(type);

        AoeSpec mining = spec.miningAoe();
        if (mining != null && breakable) {
            // Carve a cross-section (radius from the MINING emitter, EXPAND-scaled)
            // here — through vegetation too, not just solid blocks — so aiming a
            // mining ray at grass clears the plant AND the ground within its
            // radius. Non-solid blocks don't collide, so they never stop it; a
            // solid block stops it unless we also pierce.
            mineCrossSection(here, mining.radius());
            return solid && !spec.isPierce();
        }
        if (!solid) {
            return false; // no collision with grass, fluids, etc.
        }
        if (spec.isPierce() && breakable) {
            return false; // ghost through a soft block
        }
        // TODO(bounce seam): with spec.bounces() > 0, reflect off the block
        // face here and decrement instead of stopping.
        return true;
    }

    private boolean isBreakable(Material type) {
        double hardness = type.getHardness();
        return hardness >= 0 && hardness <= spec.pierceMaxHardness();
    }

    /** Break every soft block within {@code radius} of the ray point (EXPAND-scaled bore). */
    private void mineCrossSection(Location center, double radius) {
        if (!minedCells.add(blockKey(center))) {
            return; // already carved around this block cell
        }
        double r = Math.min(radius, MINING_MAX_RADIUS);
        double rSq = r * r;
        int ri = (int) Math.ceil(r);
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        boolean carved = false;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    // The centre always breaks; a neighbour only if inside the bore.
                    boolean centre = dx == 0 && dy == 0 && dz == 0;
                    if (!centre && dx * dx + dy * dy + dz * dz >= rSq) {
                        continue;
                    }
                    carved |= breakSoft(world.getBlockAt(cx + dx, cy + dy, cz + dz));
                }
            }
        }
        if (carved) {
            world.playSound(center, Sound.BLOCK_STONE_BREAK, 0.6f, 0.9f);
        }
    }

    /** Break one block if it's soft enough for this ray; returns true if it broke. */
    private boolean breakSoft(Block block) {
        Material type = block.getType();
        // Break anything within the hardness cap, including vegetation (which is
        // non-solid). The cap still excludes fluids and hard blocks (obsidian).
        if (type.isAir() || !isBreakable(type)) {
            return false;
        }
        world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                8, 0.2, 0.2, 0.2, 0.0, type.createBlockData());
        ItemStack tool = caster != null ? caster.getInventory().getItemInMainHand() : null;
        if (tool != null) {
            block.breakNaturally(tool);
        } else {
            block.breakNaturally();
        }
        return true;
    }

    private static long blockKey(Location loc) {
        return ((long) loc.getBlockX() & 0x3FFFFFFL) << 38
                | ((long) loc.getBlockZ() & 0x3FFFFFFL) << 12
                | ((long) loc.getBlockY() & 0xFFFL);
    }

    /**
     * Handle entities at {@code here}. A piercing shot contact-hits each new
     * entity and keeps going; a non-piercing shot stops at the first one (the
     * end burst does the pushing). Returns true if the projectile should stop.
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
                return true; // stop at the first entity; the payload acts here
            }
            // Pierce: deliver the full payload at each entity we pass through
            // (so EXPAND/AMPLIFY splash on every hit), then nudge it and carry
            // on. The terminus burst still fires where the shot finally stops.
            payload.detonate(world, here, caster, generation);
            contactShove(living, travel);
        }
        return false;
    }

    /** A light along-the-line push applied to entities a piercing shot passes through. */
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
        // A short melee poke keeps no visible trail at all — not even mining
        // sparks — so it reads as an instant swing rather than a flying bolt.
        // Ranged shots and mining rays (visibleTrail on) render theirs.
        if (!spec.hasVisibleTrail()) {
            return;
        }
        world.spawnParticle(Particle.CRIT, here, 1, 0.02, 0.02, 0.02, 0.0);
        if (spec.isMining()) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, here, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /**
     * Deliver the payload at {@code where} (may be null if we never had a world)
     * and end. An empty payload delivers nothing — no terminus sound or pop —
     * so a mining ray or kinetic lance simply stops.
     */
    private void stop(Location where) {
        try {
            if (where != null) {
                payload.detonate(world, where, caster, generation);
            }
        } finally {
            cancel();
        }
    }

    /** Fusion depth of this shot; reserved for the spawn-children trigger seam. */
    public int generation() {
        return generation;
    }

    /** Current (or, once stopped, final) Y — used by the self-test to observe gravity. */
    public double positionY() {
        return position.getY();
    }
}
