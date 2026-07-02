package com.xton.fusion.projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

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

    public ProjectileLauncher(Plugin plugin, AoeBurst burst, WeaponBuilder.Defaults defaults) {
        this.plugin = plugin;
        this.burst = burst;
        this.defaults = defaults;
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
            effects.add(new BurstEffect(burst, aoe));
        }
        return effects.isEmpty() ? Payload.empty() : new Payload(effects);
    }

    /**
     * Launch the shot from {@code caster}'s eye along their look direction.
     * {@code speedScale} lets a bow scale speed by draw force (1.0 for a melee
     * swing).
     */
    public void launch(Player caster, ModifierStack stack, double speedScale) {
        ProjectileSpec spec = compile(stack);
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
