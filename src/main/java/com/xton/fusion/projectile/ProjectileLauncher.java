package com.xton.fusion.projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.modifier.ProjectileSpec;
import com.xton.fusion.modifier.TrailStyle;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Turns a weapon's modifier stack into flying {@link FusionProjectile}s. A swing
 * or bow shot calls {@link #launch}: the stack compiles into a
 * {@link ProjectileSpec} (flight + payload), and one projectile per MULTISHOT
 * count is spawned, each aimed with the SPREAD cone and carrying a
 * {@link Payload} built from the spec's burst emitters. A MOB:&lt;type&gt; spec
 * hurls live entities instead; SPAWN/DELAY children come back through
 * {@link #spawnChildren} at their parent's terminus.
 *
 * <p>{@link #compile} and {@link #buildPayload} are pure (no world), so what a
 * stack produces is unit-testable without a server.
 */
public final class ProjectileLauncher {

    private final Plugin plugin;
    private final AoeBurst burst;
    private final WeaponBuilder.Defaults defaults;
    private final EnvironmentalAoe.Settings envSettings;
    private final int maxSpawnGeneration;
    private final int meleeLifetimeTicks;
    private final double meleeSpeed;

    public ProjectileLauncher(Plugin plugin, AoeBurst burst, WeaponBuilder.Defaults defaults,
                              EnvironmentalAoe.Settings envSettings, int maxSpawnGeneration,
                              int meleeLifetimeTicks, double meleeSpeed) {
        this.plugin = plugin;
        this.burst = burst;
        this.defaults = defaults;
        this.envSettings = envSettings;
        this.maxSpawnGeneration = maxSpawnGeneration;
        this.meleeSpeed = meleeSpeed;
        this.meleeLifetimeTicks = meleeLifetimeTicks;
    }

    /** The owning plugin — used to schedule projectiles (e.g. by the self-test). */
    public Plugin plugin() {
        return plugin;
    }

    /** Compile the stack into a projectile spec (flight + payload). Pure. */
    public ProjectileSpec compile(ModifierStack stack) {
        return new WeaponBuilder(defaults).compile(stack);
    }

    /**
     * Build the payload for a compiled spec: one {@link BurstEffect} per
     * <em>entity-burst</em> AOE emitter (PUSH/DAMAGE). The environmental kinds
     * (MINING/FIRE/ICE/DEPOSIT) are applied by the projectile along its flight, so
     * they're skipped here. Empty when the stack had no entity bursts — so a
     * mining ray or a fire trail delivers no terminus burst. Pure.
     */
    public Payload buildPayload(ProjectileSpec spec) {
        List<PayloadEffect> effects = new ArrayList<>();
        for (AoeSpec aoe : spec.payload()) {
            if (aoe.kind().isEnvironmental() || aoe.kind() == AoeKind.DETECT) {
                continue; // environmental: applied along the flight; DETECT: a sensor, not a burst
            }
            effects.add(new BurstEffect(burst, aoe));
        }
        return effects.isEmpty() ? Payload.empty() : new Payload(effects);
    }

    /**
     * A melee swing: a short, gravity-free poke that delivers its payload at
     * arm's length with only a subtle energy-ball wake. Flight transforms
     * (LIFETIME, MINING, ...) extend it from there.
     */
    public void launchMelee(Player caster, ModifierStack stack) {
        launch(caster, stack, 1.0, false, meleeLifetimeTicks, meleeSpeed, TrailStyle.SUBTLE);
    }

    /**
     * A bow release: a ranged, arcing shot whose speed scales with draw force
     * (a tap still fires a slow shot).
     */
    public void launchBow(Player caster, ModifierStack stack, double force) {
        double speedScale = 0.35 + 0.65 * clamp01(force);
        // Bow shots arc (gravity on); a melee poke stays straight. Gravity is
        // purely the launcher's call — no modifier touches it (yet).
        launch(caster, stack, speedScale, true, defaults.baseLifetimeTicks(), defaults.baseSpeed(),
                TrailStyle.BRIGHT);
    }

    /**
     * Launch the shot from {@code caster}'s eye along their look direction. The
     * weapon-type flight (gravity, base lifetime, trail) is seeded before the
     * modifier stack compiles, so flight transforms build on top of it.
     */
    private void launch(Player caster, ModifierStack stack, double speedScale,
                        boolean gravity, int baseLifetimeTicks, double baseSpeed, TrailStyle trail) {
        WeaponBuilder builder = new WeaponBuilder(defaults);
        builder.projectile().setSpeed(baseSpeed);
        builder.projectile().setLifetimeTicks(baseLifetimeTicks);
        builder.projectile().setGravity(gravity);
        builder.projectile().setTrailStyle(trail);
        ProjectileSpec spec = builder.compile(stack);

        Payload payload = buildPayload(spec);
        Location origin = caster.getEyeLocation();
        Vector aim = origin.getDirection().normalize();
        double speed = Math.max(0.05, spec.speed() * speedScale);
        int count = Math.max(1, spec.count());

        // MOB shots hurl live entities instead of custom bolts — the mob is the payload.
        if (spec.mobType() != null) {
            for (int i = 0; i < count; i++) {
                Vector dir = scatter(aim, spec.spreadDegrees());
                spawnMobShot(caster.getWorld(), origin.clone(), dir.multiply(speed), spec);
            }
            return;
        }

        // One Shot per cast: caster, generation 0, and a single shared TELEPORT
        // latch so the whole volley (and any SPAWN children) teleports at most once.
        Shot shot = new Shot(caster, 0, maxSpawnGeneration, envSettings, this, new AtomicBoolean(false));
        for (int i = 0; i < count; i++) {
            Vector dir = scatter(aim, spec.spreadDegrees());
            Vector velocity = dir.multiply(speed);
            new FusionProjectile(plugin, payload, spec, caster.getWorld(),
                    origin.clone(), velocity, shot).start();
        }
    }

    /**
     * Spawn the MOB shot's live entity at {@code origin} and fling it with
     * {@code velocity}, letting vanilla physics carry it. Returns the entity (or
     * null if the type is unset / the spawn fails).
     */
    public Entity spawnMobShot(World world, Location origin, Vector velocity, ProjectileSpec spec) {
        if (world == null || spec.mobType() == null) {
            return null;
        }
        Entity entity = world.spawnEntity(origin, spec.mobType());
        entity.setVelocity(velocity);
        return entity;
    }

    /** How far off the terminus children spawn, along their heading, to clear the impacted face. */
    private static final double SPAWN_OFFSET = 0.6;

    /**
     * Launch a SPAWN emitter's fresh children at a parent's terminus. Each child
     * carries its own compiled flight/payload; it inherits nothing from the
     * parent but the cast context (one generation deeper, so recursion is capped).
     * A child's own MULTISHOT/SPREAD scatter it around the parent's heading. The
     * heading is the parent's velocity <em>reflected off the surface</em> when it
     * hit a block, and children spawn nudged along it, so a shot that ends against
     * a wall scatters its children back into the open instead of into the wall.
     */
    public void spawnChildren(List<ProjectileSpec> children, Location at, Vector heading, Shot parentShot) {
        Shot childShot = parentShot.deeper();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Vector aim = heading.lengthSquared() > 1.0e-6 ? heading.clone().normalize() : new Vector(0, 1, 0);
        for (ProjectileSpec child : children) {
            // A DELAY child detonates in place, so spawn it exactly at the terminus;
            // a flying SPAWN child is nudged off the surface so it clears the face.
            boolean inPlace = child.spawnDelayTicks() > 0 || child.speed() < 0.1;
            Location origin = inPlace ? at.clone() : at.clone().add(aim.clone().multiply(SPAWN_OFFSET));
            if (child.spawnDelayTicks() > 0) {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> launchChildVolley(child, world, origin, aim, childShot),
                        child.spawnDelayTicks());
            } else {
                launchChildVolley(child, world, origin, aim, childShot);
            }
        }
    }

    /** Launch one child spec's volley (its MULTISHOT count, scattered by its SPREAD). */
    private void launchChildVolley(ProjectileSpec child, World world, Location origin, Vector aim, Shot childShot) {
        if (!world.isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) {
            return; // the area unloaded during a DELAY — drop the charge rather than force-load
        }
        Payload payload = buildPayload(child);
        double speed = Math.max(0.05, child.speed());
        int count = Math.max(1, child.count());
        for (int i = 0; i < count; i++) {
            Vector dir = scatter(aim, child.spreadDegrees());
            Vector velocity = dir.multiply(speed);
            new FusionProjectile(plugin, payload, child, world,
                    origin.clone(), velocity, childShot).start();
        }
    }

    /**
     * Fire a single projectile directly with a fresh cast context — the entry the
     * self-test uses to launch a compiled spec against a live world without a
     * player. Returns the projectile so the test can observe it.
     */
    public FusionProjectile fireDirect(World world, Location origin, Vector velocity, ProjectileSpec spec) {
        Payload payload = buildPayload(spec);
        Shot shot = new Shot(null, 0, maxSpawnGeneration, envSettings, this, new AtomicBoolean(false));
        FusionProjectile bolt = new FusionProjectile(plugin, payload, spec, world, origin, velocity, shot);
        bolt.start();
        return bolt;
    }

    private static double clamp01(double force) {
        return Math.clamp(force, 0.0, 1.0);
    }

    /** Offset a direction by a random angle within a {@code spreadDegrees} cone. */
    private Vector scatter(Vector aim, double spreadDegrees) {
        if (spreadDegrees <= 0) {
            return aim.clone();
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double polar = Math.toRadians(rng.nextDouble() * spreadDegrees);
        double azimuth = rng.nextDouble() * 2 * Math.PI;

        // Any axis perpendicular to the aim will do — the azimuth roll below hides
        // which one was picked.
        Vector right = aim.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-6) {
            right = new Vector(1, 0, 0); // aim is vertical; any horizontal axis is perpendicular
        }
        right.normalize();

        // Tip the aim by the polar angle, then roll that tilt around the original
        // aim — a uniform cone whatever direction the shot faces (a world-yaw
        // rotation, by contrast, degenerates to a no-op on a vertical aim).
        Vector dir = aim.clone().rotateAroundAxis(right, polar);
        return dir.rotateAroundAxis(aim, azimuth).normalize();
    }
}
