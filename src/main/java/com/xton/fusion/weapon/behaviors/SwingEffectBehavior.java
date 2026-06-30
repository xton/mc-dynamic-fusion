package com.xton.fusion.weapon.behaviors;

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
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.util.Scheduler;

/**
 * Resolves a weapon's modifier stack into a burst that shoves nearby entities
 * outward (or, with INVERT, pulls them inward), then applies the area / chain /
 * repeat / delay / persist modifiers.
 *
 * <ul>
 *   <li><b>base</b> — shove entities within a small radius around the origin.</li>
 *   <li><b>NOVA</b> — sets the burst radius.</li>
 *   <li><b>EXPAND</b> — adds to the radius; stacks.</li>
 *   <li><b>CHAIN</b> — hops to the nearest entities beyond the burst.</li>
 *   <li><b>REPEAT</b> — fires again a few times in succession.</li>
 *   <li><b>DELAYED</b> — holds the effect for a fuse before firing.</li>
 *   <li><b>INVERT</b> — implodes (pulls inward) instead of shoving out.</li>
 *   <li><b>PERSIST</b> — drops a lingering field that re-pulses the burst.</li>
 * </ul>
 */
public final class SwingEffectBehavior {

    /** Tunables resolved from config. */
    public record Settings(double baseRadius, double basePower, double chainRange,
                           long repeatDelayTicks, long persistIntervalTicks, boolean affectPlayers) {
    }

    private final Scheduler scheduler;
    private final Settings settings;

    public SwingEffectBehavior(Scheduler scheduler, Settings settings) {
        this.scheduler = scheduler;
        this.settings = settings;
    }

    /** Melee swing: burst around the caster, honoring DELAYED + REPEAT + PERSIST timing. */
    public void execute(Player caster, ModifierStack stack) {
        ModifierContext ctx = build(stack);
        int repeats = 1 + Math.max(0, ctx.getRepeatCount());
        long base = Math.max(0, ctx.getDelayTicks());
        for (int i = 0; i < repeats; i++) {
            long delay = base + (long) i * settings.repeatDelayTicks();
            scheduler.runLater(() -> {
                if (caster.isValid()) {
                    applyBurst(caster.getWorld(), caster.getLocation(), ctx, caster);
                }
            }, delay);
        }
        if (ctx.getPersistTicks() > 0) {
            // Lingering field stays at the cast location (the caster can walk out).
            schedulePersist(caster.getWorld(), caster.getLocation().clone(), ctx, caster, base);
        }
    }

    /** Single burst at a point (e.g. where a fused bow's projectile landed). */
    public void burstAt(Location origin, ModifierStack stack) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        ModifierContext ctx = build(stack);
        applyBurst(origin.getWorld(), origin, ctx, null);
        if (ctx.getPersistTicks() > 0) {
            schedulePersist(origin.getWorld(), origin.clone(), ctx, null, 0);
        }
    }

    private ModifierContext build(ModifierStack stack) {
        ModifierContext ctx = new ModifierContext()
                .setRadius(settings.baseRadius())
                .setPower(settings.basePower());
        stack.applyTo(ctx);
        return ctx;
    }

    private void schedulePersist(World world, Location origin, ModifierContext ctx,
                                 Entity exclude, long startDelay) {
        long interval = Math.max(1, settings.persistIntervalTicks());
        long duration = ctx.getPersistTicks();
        for (long t = interval; t <= duration; t += interval) {
            scheduler.runLater(() -> applyBurst(world, origin, ctx, exclude), startDelay + t);
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
