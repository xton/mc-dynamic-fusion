package com.xton.fusion.projectile;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
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
        // Grenade charge-up: between pulses a glowing dot sits at the point and
        // blinks faster and faster; on each interval it detonates (applyBurst,
        // which draws the boom and applies the effect). Only the pulse does
        // anything — the dot is pure visual charge, so the visuals match the
        // mechanic instead of implying continuous damage.
        for (long t = 1; t <= duration; t++) {
            final long tick = t;
            final long intoCycle = t % interval;
            final boolean pulse = intoCycle == 0;
            final long toNext = pulse ? interval : interval - intoCycle;
            scheduler.runLater(() -> {
                if (pulse) {
                    applyBurst(world, where, spec, caster);
                } else {
                    chargeDot(world, where, toNext);
                }
            }, tick);
        }
    }

    /** A glowing dot at the retrigger point that flashes faster as the pulse nears. */
    private void chargeDot(World world, Location where, long toNext) {
        Location dot = where.clone().add(0, 0.6, 0);
        world.spawnParticle(Particle.SMALL_FLAME, dot, 1, 0.0, 0.0, 0.0, 0.0); // steady marker
        long blinkPeriod = Math.max(1, Math.min(4, toNext / 3));
        if (toNext % blinkPeriod == 0) {
            world.spawnParticle(Particle.END_ROD, dot, 1, 0.02, 0.02, 0.02, 0.0); // accelerating blink
        }
    }

    private void applyBurst(World world, Location where, AoeSpec spec, Player caster) {
        double radius = spec.radius();
        Vector center = where.toVector();

        List<LivingEntity> hit = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(where, radius, radius, radius)) {
            LivingEntity target = asTarget(caster, entity, spec);
            if (target == null) {
                continue;
            }
            affect(target, center, spec, caster);
            hit.add(target);
        }
        boom(world, where, spec);

        if (spec.chainCount() > 0) {
            chain(world, caster, where, hit, spec);
        }
    }

    /** Apply the spec's effect to one target: heal (HEAL), damage (DAMAGE), or shove (PUSH/PULL). */
    private void affect(LivingEntity target, Vector center, AoeSpec spec, Player caster) {
        if (spec.kind() == AoeKind.HEAL) {
            AttributeInstance attr = target.getAttribute(Attribute.MAX_HEALTH);
            double max = attr != null ? attr.getValue() : 20.0;
            target.setHealth(Math.min(max, target.getHealth() + spec.power()));
            return;
        }
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

    /**
     * Returns the entity as a valid target, or null if it should be skipped. HEAL
     * is friendly: it mends the caster and players (regardless of affect-players)
     * but skips hostile mobs. Every other burst never hits the caster and respects
     * affect-players.
     */
    private LivingEntity asTarget(Player caster, Entity entity, AoeSpec spec) {
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        boolean heal = spec.kind() == AoeKind.HEAL;
        if (entity.equals(caster)) {
            return heal ? living : null; // a heal burst mends its caster too
        }
        if (entity instanceof Player) {
            return (heal || settings.affectPlayers()) ? living : null;
        }
        if (heal && entity instanceof Monster) {
            return null; // don't heal what's trying to kill you
        }
        return living;
    }

    private void chain(World world, Player caster, Location where,
                       List<LivingEntity> hit, AoeSpec spec) {
        Location from = where;
        Vector center = where.toVector();
        for (int i = 0; i < spec.chainCount(); i++) {
            LivingEntity next = nearestUnhit(world, caster, from, hit, spec);
            if (next == null) {
                break;
            }
            link(world, from, next.getLocation());
            affect(next, i == 0 ? center : from.toVector(), spec, caster);
            hit.add(next);
            from = next.getLocation();
        }
    }

    private LivingEntity nearestUnhit(World world, Player caster, Location from, List<LivingEntity> hit, AoeSpec spec) {
        double range = settings.chainRange();
        LivingEntity best = null;
        double bestSq = range * range;
        for (Entity entity : world.getNearbyEntities(from, range, range, range)) {
            LivingEntity target = asTarget(caster, entity, spec);
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

    /**
     * The "it goes off HERE" visual: a small explosion sprite right at the point,
     * plus a little kind-specific flavour. Deliberately centred (not a spread of
     * sparks) so a single burst reads as a detonation at that spot.
     */
    private void boom(World world, Location where, AoeSpec spec) {
        Location center = where.clone().add(0, 0.5, 0);
        double radius = spec.radius();
        switch (spec.kind()) {
            case HEAL -> { // a friendly sparkle, no explosion
                world.spawnParticle(Particle.HEART, center, 6, radius / 3, 0.3, radius / 3, 0.0);
                world.spawnParticle(Particle.HAPPY_VILLAGER, center, 8, radius / 3, 0.3, radius / 3, 0.0);
                world.playSound(where, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
            }
            case DAMAGE -> {
                world.spawnParticle(Particle.EXPLOSION, center, 1);
                world.spawnParticle(Particle.CRIT, center, 10, radius / 3, 0.2, radius / 3, 0.05);
                world.playSound(where, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
            }
            default -> { // PUSH / PULL
                world.spawnParticle(Particle.EXPLOSION, center, 1);
                world.spawnParticle(Particle.SWEEP_ATTACK, center, 4, radius / 3, 0.2, radius / 3, 0.0);
                world.playSound(where, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
            }
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
