package com.xton.fusion.projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.modifier.ProjectileSpec;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Turns a weapon's modifier stack into flying {@link FusionProjectile}s. A swing
 * or bow shot calls {@link #launch}: the stack compiles into a
 * {@link ProjectileSpec} (flight + payload), and one projectile per MULTISHOT
 * count is spawned, each aimed with the SPREAD cone and carrying a
 * {@link Payload} built from the spec's AOE emitters.
 *
 * <p>{@link #compile} and {@link #buildPayload} are pure (no world), so what a
 * stack produces is unit-testable without a server.
 */
public final class ProjectileLauncher {

    private final Plugin plugin;
    private final AoeBurst burst;
    private final WeaponBuilder.Defaults defaults;
    private final int meleeLifetimeTicks;

    public ProjectileLauncher(Plugin plugin, AoeBurst burst, WeaponBuilder.Defaults defaults,
                              int meleeLifetimeTicks) {
        this.plugin = plugin;
        this.burst = burst;
        this.defaults = defaults;
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
     * Build the payload for a compiled spec: one {@link BurstEffect} per AOE
     * emitter. Empty when the stack had no emitters — so a mining ray or kinetic
     * lance delivers nothing at its terminus. Pure.
     *
     * <p>Seam: a future spawn effect (cluster bomb) would be added here from a
     * spawn emitter in the spec, gated by the projectile's generation.
     */
    public Payload buildPayload(ProjectileSpec spec) {
        List<PayloadEffect> effects = new ArrayList<>();
        for (AoeSpec aoe : spec.payload()) {
            if (aoe.kind() == AoeKind.MINING) {
                continue; // carved along the flight, not delivered as a terminus burst
            }
            effects.add(new BurstEffect(burst, aoe));
        }
        return effects.isEmpty() ? Payload.empty() : new Payload(effects);
    }

    /**
     * A melee swing: a short, gravity-free poke that delivers its payload at
     * arm's length with no visible flight trail. Flight transforms (LIFETIME,
     * MINING, ...) extend it from there.
     */
    public void launchMelee(Player caster, ModifierStack stack) {
        launch(caster, stack, 1.0, false, meleeLifetimeTicks, false);
    }

    /**
     * A bow release: a ranged, arcing shot whose speed scales with draw force
     * (a tap still fires a slow shot).
     */
    public void launchBow(Player caster, ModifierStack stack, double force) {
        double speedScale = 0.35 + 0.65 * clamp01(force);
        // Bow shots arc (gravity on); a melee poke stays straight. Gravity is
        // purely the launcher's call — no modifier touches it (yet).
        launch(caster, stack, speedScale, true, defaults.baseLifetimeTicks(), true);
    }

    /**
     * Launch the shot from {@code caster}'s eye along their look direction. The
     * weapon-type flight (gravity, base lifetime, trail) is seeded before the
     * modifier stack compiles, so flight transforms build on top of it.
     */
    private void launch(Player caster, ModifierStack stack, double speedScale,
                        boolean gravity, int baseLifetimeTicks, boolean visibleTrail) {
        WeaponBuilder builder = new WeaponBuilder(defaults);
        builder.projectile().setLifetimeTicks(baseLifetimeTicks);
        builder.projectile().setGravity(gravity);
        builder.projectile().setVisibleTrail(visibleTrail);
        ProjectileSpec spec = builder.compile(stack);

        Payload payload = buildPayload(spec);
        Location origin = caster.getEyeLocation();
        Vector aim = origin.getDirection().normalize();
        double speed = Math.max(0.05, spec.speed() * speedScale);
        int count = Math.max(1, spec.count());

        for (int i = 0; i < count; i++) {
            Vector dir = scatter(aim, spec.spreadDegrees());
            Vector velocity = dir.multiply(speed);
            new FusionProjectile(plugin, payload, spec, caster.getWorld(),
                    origin.clone(), velocity, caster, 0).start();
        }
    }

    private static double clamp01(double force) {
        return force < 0 ? 0.0 : Math.min(1.0, force);
    }

    /** Offset a direction by a random angle within a {@code spreadDegrees} cone. */
    private Vector scatter(Vector aim, double spreadDegrees) {
        if (spreadDegrees <= 0) {
            return aim.clone();
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double dyaw = Math.toRadians((rng.nextDouble() * 2 - 1) * spreadDegrees);
        double dpitch = Math.toRadians((rng.nextDouble() * 2 - 1) * spreadDegrees);

        Vector dir = aim.clone();
        Vector right = aim.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-6) {
            right = new Vector(1, 0, 0); // aim was vertical; pick any horizontal axis
        }
        right.normalize();
        dir.rotateAroundY(dyaw);
        dir.rotateAroundAxis(right, dpitch);
        return dir.normalize();
    }
}
