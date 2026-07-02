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

import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.util.Scheduler;

/**
 * Fires a single {@link AoeSpec} area burst where a projectile terminates: it
 * shoves (PUSH) or damages (DAMAGE) the entities in range, optionally chaining
 * to further entities and re-pulsing (PERSIST). Each burst element in a
 * projectile's payload is fired through here.
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
     * Fire {@code spec} at {@code where}. {@code caster} (the wielder) is never
     * hit. Schedules PERSIST pulses if the spec asks for them.
     */
    public void fire(World world, Location where, AoeSpec spec, Player caster) {
        if (world == null || where == null) {
            return;
        }
        applyBurst(world, where, spec, caster);
        if (spec.persistTicks() > 0) {
            schedulePersist(world, where.clone(), spec, caster);
        }
    }

    private void schedulePersist(World world, Location where, AoeSpec spec, Player caster) {
        long interval = Math.max(1, settings.persistIntervalTicks());
        long duration = spec.persistTicks();
        for (long t = interval; t <= duration; t += interval) {
            scheduler.runLater(() -> applyBurst(world, where, spec, caster), t);
        }
    }

    private void applyBurst(World world, Location where, AoeSpec spec, Player caster) {
        double radius = spec.radius();
        Vector center = where.toVector();

        List<LivingEntity> hit = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(where, radius, radius, radius)) {
            LivingEntity target = asTarget(caster, entity);
            if (target == null) {
                continue;
            }
            affect(target, center, spec, caster);
            hit.add(target);
        }
        particles(world, where, spec);

        if (spec.chainCount() > 0) {
            chain(world, caster, where, hit, spec);
        }
    }

    /** Apply the spec's effect to one target: a shove (PUSH) or damage (DAMAGE). */
    private void affect(LivingEntity target, Vector center, AoeSpec spec, Player caster) {
        if (spec.kind() == AoeKind.DAMAGE) {
            target.damage(spec.power(), caster);
            return;
        }
        Vector dir = target.getLocation().toVector().subtract(center); // outward from centre
        if (dir.lengthSquared() < 1.0e-6) {
            dir = new Vector(0, 1, 0);
        }
        dir.normalize();
        if (spec.inverted()) {
            dir.multiply(-1); // pull inward
        }
        dir.multiply(spec.power());
        dir.setY(spec.inverted() ? 0.15 : Math.max(dir.getY(), 0.3));
        target.setVelocity(target.getVelocity().add(dir));
    }

    /** Returns the entity as a valid target, or null if it should be skipped. */
    private LivingEntity asTarget(Player caster, Entity entity) {
        if (entity.equals(caster) || !(entity instanceof LivingEntity living)) {
            return null;
        }
        if (!settings.affectPlayers() && entity instanceof Player) {
            return null;
        }
        return living;
    }

    private void chain(World world, Player caster, Location where,
                       List<LivingEntity> hit, AoeSpec spec) {
        Location from = where;
        Vector center = where.toVector();
        for (int i = 0; i < spec.chainCount(); i++) {
            LivingEntity next = nearestUnhit(world, caster, from, hit);
            if (next == null) {
                break;
            }
            link(world, from, next.getLocation());
            affect(next, i == 0 ? center : from.toVector(), spec, caster);
            hit.add(next);
            from = next.getLocation();
        }
    }

    private LivingEntity nearestUnhit(World world, Player caster, Location from, List<LivingEntity> hit) {
        double range = settings.chainRange();
        LivingEntity best = null;
        double bestSq = range * range;
        for (Entity entity : world.getNearbyEntities(from, range, range, range)) {
            LivingEntity target = asTarget(caster, entity);
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

    private void particles(World world, Location where, AoeSpec spec) {
        Location center = where.clone().add(0, 1, 0);
        double radius = spec.radius();
        if (spec.kind() == AoeKind.DAMAGE) {
            world.spawnParticle(Particle.CRIT, center, 20, radius / 2, 0.4, radius / 2, 0.1);
            world.spawnParticle(Particle.LAVA, center, 6, radius / 3, 0.2, radius / 3, 0.0);
            world.playSound(where, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
        } else {
            world.spawnParticle(Particle.SWEEP_ATTACK, center, 12, radius / 2, 0.4, radius / 2, 0.0);
            world.spawnParticle(Particle.CLOUD, center, 24, 0.3, 0.3, 0.3, 0.05);
            world.playSound(where, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        }
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
