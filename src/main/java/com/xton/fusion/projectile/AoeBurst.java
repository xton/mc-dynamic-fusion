package com.xton.fusion.projectile;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.xton.fusion.modifier.ModifierContext;
import com.xton.fusion.util.Scheduler;

/**
 * The area action a projectile fires where it triggers: a burst that shoves
 * nearby entities outward (or, with INVERT, pulls them inward), optionally
 * chaining to further entities and leaving a lingering PERSIST field. This is
 * the Noita-style "on-trigger" payload — it reads the already-resolved burst
 * fields off the {@link ModifierContext} and acts on the world.
 *
 * <p>Extracted from the old swing behaviour so both swing and bow now route
 * through a projectile that ends in this burst.
 */
public final class AoeBurst {

    /** Tunables resolved from config. */
    public record Settings(double chainRange, long persistIntervalTicks, boolean affectPlayers) {
    }

    private final Scheduler scheduler;
    private final Settings settings;

    public AoeBurst(Scheduler scheduler, Settings settings) {
        this.scheduler = scheduler;
        this.settings = settings;
    }

    /**
     * Fire the burst at {@code origin}. {@code exclude} (usually the caster) is
     * never pushed. Schedules PERSIST pulses if the context asks for them.
     */
    public void fire(World world, Location origin, ModifierContext ctx, Entity exclude) {
        if (world == null || origin == null) {
            return;
        }
        applyBurst(world, origin, ctx, exclude);
        if (ctx.getPersistTicks() > 0) {
            schedulePersist(world, origin.clone(), ctx, exclude);
        }
    }

    private void schedulePersist(World world, Location origin, ModifierContext ctx, Entity exclude) {
        long interval = Math.max(1, settings.persistIntervalTicks());
        long duration = ctx.getPersistTicks();
        for (long t = interval; t <= duration; t += interval) {
            scheduler.runLater(() -> applyBurst(world, origin, ctx, exclude), t);
        }
    }

    private void applyBurst(World world, Location origin, ModifierContext ctx, Entity exclude) {
        double radius = ctx.getRadius() + ctx.getExpandBonus();
        double power = ctx.getPower();
        boolean inverted = ctx.isInverted();
        Vector center = origin.toVector();

        List<LivingEntity> hit = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(origin, radius, radius, radius)) {
            LivingEntity target = asTarget(exclude, entity);
            if (target == null) {
                continue;
            }
            shove(target, center, power, inverted);
            hit.add(target);
        }
        burst(world, origin, radius);

        if (ctx.getChainCount() > 0) {
            chain(world, exclude, origin, hit, ctx.getChainCount(), power, inverted);
        }
    }

    /** Returns the entity as a valid push target, or null if it should be skipped. */
    private LivingEntity asTarget(Entity exclude, Entity entity) {
        if (entity.equals(exclude) || !(entity instanceof LivingEntity living)) {
            return null;
        }
        if (!settings.affectPlayers() && entity instanceof Player) {
            return null;
        }
        return living;
    }

    private void shove(LivingEntity target, Vector center, double power, boolean inverted) {
        Vector dir = target.getLocation().toVector().subtract(center); // outward from centre
        if (dir.lengthSquared() < 1.0e-6) {
            dir = new Vector(0, 1, 0);
        }
        dir.normalize();
        if (inverted) {
            dir.multiply(-1); // pull inward
        }
        dir.multiply(power);
        dir.setY(inverted ? 0.15 : Math.max(dir.getY(), 0.3));
        target.setVelocity(target.getVelocity().add(dir));
    }

    private void chain(World world, Entity exclude, Location origin,
                       List<LivingEntity> hit, int hops, double power, boolean inverted) {
        Location from = origin;
        for (int i = 0; i < hops; i++) {
            LivingEntity next = nearestUnhit(world, exclude, from, hit);
            if (next == null) {
                break;
            }
            link(world, from, next.getLocation());
            shove(next, from.toVector(), power, inverted);
            hit.add(next);
            from = next.getLocation();
        }
    }

    private LivingEntity nearestUnhit(World world, Entity exclude, Location from, List<LivingEntity> hit) {
        double range = settings.chainRange();
        LivingEntity best = null;
        double bestSq = range * range;
        for (Entity entity : world.getNearbyEntities(from, range, range, range)) {
            LivingEntity target = asTarget(exclude, entity);
            if (target == null || hit.contains(target)) {
                continue;
            }
            double distSq = entity.getLocation().distanceSquared(from);
            if (distSq < bestSq) {
                bestSq = distSq;
                best = target;
            }
        }
        return best;
    }

    private void burst(World world, Location origin, double radius) {
        Location center = origin.clone().add(0, 1, 0);
        world.spawnParticle(Particle.SWEEP_ATTACK, center, 12, radius / 2, 0.4, radius / 2, 0.0);
        world.spawnParticle(Particle.CLOUD, center, 24, 0.3, 0.3, 0.3, 0.05);
        world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
    }

    /** Draw a short particle arc from one point to the next chain target. */
    private void link(World world, Location a, Location b) {
        Vector step = b.toVector().subtract(a.toVector());
        int points = Math.max(1, (int) (step.length() * 2));
        step.multiply(1.0 / points);
        Location p = a.clone().add(0, 1, 0);
        for (int i = 0; i < points; i++) {
            p.add(step);
            world.spawnParticle(Particle.CRIT, p, 1, 0, 0, 0, 0);
        }
        world.playSound(b, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
    }
}
