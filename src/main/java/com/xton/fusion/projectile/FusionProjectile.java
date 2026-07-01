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

import com.xton.fusion.modifier.ModifierContext;

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
 *       it is (DELAYED lengthens this);</li>
 *   <li><b>gravity</b> / <b>bounces</b> — seams wired to fields, ready to grow.</li>
 * </ul>
 *
 * <p>When it stops (hits a hard block, hits an entity without pierce, or
 * expires) it fires its {@link AoeBurst} payload at that point. A non-piercing
 * bolt therefore "triggers where it lands"; a piercing shot contact-hits along
 * the line and bursts once at the end.
 */
public final class FusionProjectile extends BukkitRunnable {

    /** Blocks per sub-step when scanning a tick of travel. */
    private static final double STEP = 0.3;
    /** Half-extent of the entity contact box, in blocks. */
    private static final double HIT_RADIUS = 0.65;
    /** Downward acceleration per tick when gravity is on (matches a light arrow). */
    private static final double GRAVITY_PER_TICK = 0.05;

    private final Plugin plugin;
    private final AoeBurst burst;
    private final ModifierContext ctx;
    private final World world;
    private final Player caster;
    private final int generation;

    private final Vector position;
    private final Vector velocity;
    private final Set<UUID> contacted = new HashSet<>();

    private int age;

    public FusionProjectile(Plugin plugin, AoeBurst burst, ModifierContext ctx,
                            World world, Location origin, Vector velocity,
                            Player caster, int generation) {
        this.plugin = plugin;
        this.burst = burst;
        this.ctx = ctx;
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
        if (ctx.hasGravity()) {
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

        if (++age >= Math.max(1, ctx.getLifetimeTicks())) {
            stop(position.toLocation(world)); // expired: trigger where it is
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
        if (type.isAir() || !type.isSolid()) {
            return false; // nothing to collide with
        }
        double hardness = type.getHardness();
        boolean breakable = hardness >= 0 && hardness <= ctx.getPierceMaxHardness();

        if (ctx.isMining() && breakable) {
            ItemStack tool = caster != null ? caster.getInventory().getItemInMainHand() : null;
            if (tool != null) {
                block.breakNaturally(tool);
            } else {
                block.breakNaturally();
            }
            return false; // bored through
        }
        if (ctx.isPierce() && breakable) {
            return false; // ghost through a soft block
        }
        // TODO(bounce seam): with ctx.getBounces() > 0, reflect off the block
        // face here and decrement instead of stopping.
        return true;
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
            if (!ctx.isPierce()) {
                return true; // stop at the first entity; burst pushes it
            }
            contactShove(living, travel); // pierce: nudge and continue
        }
        return false;
    }

    /** A light along-the-line push applied to entities a piercing shot passes through. */
    private void contactShove(LivingEntity target, Vector travel) {
        Vector push = travel.clone();
        if (push.lengthSquared() < 1.0e-6) {
            return;
        }
        push.normalize().multiply(ctx.getPower() * 0.6);
        push.setY(Math.max(push.getY(), 0.2));
        target.setVelocity(target.getVelocity().add(push));
    }

    private void trail(Location here) {
        world.spawnParticle(Particle.CRIT, here, 1, 0.02, 0.02, 0.02, 0.0);
        if (ctx.isMining()) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, here, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Fire the payload at {@code where} (may be null if we never had a world) and end. */
    private void stop(Location where) {
        try {
            if (where != null) {
                world.playSound(where, Sound.ENTITY_ARROW_HIT, 0.6f, 1.6f);
                burst.fire(world, where, ctx, caster);
            }
        } finally {
            cancel();
        }
    }

    /** Fusion depth of this shot; reserved for the spawn-children trigger seam. */
    public int generation() {
        return generation;
    }
}
