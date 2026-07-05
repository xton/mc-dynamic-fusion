package com.xton.fusion.projectile;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.util.Scheduler;

/**
 * Fires a single {@link AoeSpec} entity burst where a projectile terminates: it
 * shoves (PUSH — or drags, when inverted as PULL), damages (DAMAGE), or mends
 * (HEAL) the entities in range, optionally chaining to further entities and
 * re-pulsing (PERSIST). Each burst element in a projectile's payload is fired
 * through here.
 */
public final class AoeBurst {

    /** Tunables resolved from config. */
    public record Settings(double chainRange, long persistIntervalTicks, boolean affectPlayers) {
    }

    /** A DAMAGE burst at or above this radius grows a real explosion sprite (a base hit doesn't). */
    private static final double DAMAGE_BLAST_RADIUS = 4.5;

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
        // Grenade charge-up: a small glowing block sits at the point and swells
        // toward each pulse, then flashes and shrinks when it detonates (applyBurst,
        // which draws the boom and applies the effect). A BlockDisplay — no
        // particles — so it reads as a discrete charging marker, not smoky fire.
        BlockDisplay marker = spawnChargeMarker(world, where);
        for (long t = 1; t <= duration; t++) {
            final long tick = t;
            final long intoCycle = t % interval;
            final boolean pulse = intoCycle == 0;
            final long toNext = pulse ? interval : interval - intoCycle;
            scheduler.runLater(() -> {
                if (pulse) {
                    applyBurst(world, where, spec, caster);
                    scaleMarker(marker, 0.85f); // flash big on the pulse
                } else {
                    pulseMarker(marker, toNext, interval);
                }
            }, tick);
        }
        scheduler.runLater(() -> removeMarker(marker), duration + 1);
    }

    /** A small glowing block-display at the retrigger point (no particles). */
    private BlockDisplay spawnChargeMarker(World world, Location where) {
        try {
            return world.spawn(where.clone().add(0, 0.4, 0), BlockDisplay.class, d -> {
                d.setBlock(Material.REDSTONE_BLOCK.createBlockData());
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
                scaleMarker(d, 0.2f);
            });
        } catch (Exception e) {
            return null; // display entities unavailable — skip the marker, mechanic still fires
        }
    }

    /** Swell the marker toward the next pulse (bigger as the pulse nears). */
    private void pulseMarker(BlockDisplay marker, long toNext, long interval) {
        double frac = 1.0 - (double) toNext / Math.max(1, interval);
        scaleMarker(marker, (float) (0.2 + 0.5 * frac));
    }

    private void scaleMarker(BlockDisplay marker, float s) {
        if (marker == null || !marker.isValid()) {
            return;
        }
        Transformation t = marker.getTransformation();
        marker.setTransformation(new Transformation(
                new Vector3f(-s / 2f, 0f, -s / 2f), t.getLeftRotation(),
                new Vector3f(s, s, s), t.getRightRotation()));
    }

    private void removeMarker(BlockDisplay marker) {
        if (marker != null && marker.isValid()) {
            marker.remove();
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
    /**
     * The "it goes off HERE" visual, scaled to the burst so the picture matches the
     * magnitude: a base DAMAGE hit is just a small red spark (red = damage), and
     * only an EXPANDed one grows a real blast; HEAL sparkles; PUSH/PULL keep the
     * explosive shove. Centred, not a spray, so it reads as a detonation at the spot.
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
                // Red spark sized to the radius; a big (EXPANDed) hit adds a blast.
                int count = (int) Math.max(6, radius * 4);
                float dustSize = (float) Math.min(3.0, radius / 2.0);
                world.spawnParticle(Particle.DUST, center, count, radius / 3, 0.25, radius / 3, 0.0,
                        new Particle.DustOptions(Color.fromRGB(220, 30, 30), dustSize));
                world.spawnParticle(Particle.CRIT, center, count / 2, radius / 3, 0.2, radius / 3, 0.05);
                if (radius >= DAMAGE_BLAST_RADIUS) {
                    world.spawnParticle(Particle.EXPLOSION, center, 1);
                    world.playSound(where, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
                } else {
                    world.playSound(where, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 0.9f);
                }
            }
            default -> { // PUSH / PULL: the explosive shove
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
