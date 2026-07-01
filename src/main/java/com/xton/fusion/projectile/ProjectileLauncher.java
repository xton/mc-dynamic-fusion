package com.xton.fusion.projectile;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.xton.fusion.modifier.ModifierContext;
import com.xton.fusion.modifier.ModifierStack;

/**
 * Turns a weapon's modifier stack into flying {@link FusionProjectile}s. A swing
 * or bow shot calls {@link #launch}: the stack resolves into a
 * {@link ModifierContext} (starting from the configured base spec), and one
 * projectile per MULTISHOT count is spawned, each aimed with the SPREAD cone.
 *
 * <p>{@link #buildContext} is pure (no world), so the spec a stack produces is
 * unit-testable without a server.
 */
public final class ProjectileLauncher {

    /** Base spec every shot starts from before modifiers apply. */
    public record Settings(double baseRadius, double basePower, double baseSpeed,
                           int baseLifetimeTicks, double pierceMaxHardness) {
    }

    private final Plugin plugin;
    private final AoeBurst burst;
    private final Settings settings;

    public ProjectileLauncher(Plugin plugin, AoeBurst burst, Settings settings) {
        this.plugin = plugin;
        this.burst = burst;
        this.settings = settings;
    }

    /** Resolve the base spec + stack into a context. Pure; safe to unit-test. */
    public ModifierContext buildContext(ModifierStack stack) {
        ModifierContext ctx = new ModifierContext()
                .setRadius(settings.baseRadius())
                .setPower(settings.basePower())
                .setSpeed(settings.baseSpeed())
                .setLifetimeTicks(settings.baseLifetimeTicks())
                .setPierceMaxHardness(settings.pierceMaxHardness());
        stack.applyTo(ctx);
        return ctx;
    }

    /**
     * Launch the shot from {@code caster}'s eye along their look direction.
     * {@code speedScale} lets a bow scale speed by draw force (1.0 for a melee
     * swing).
     */
    public void launch(Player caster, ModifierStack stack, double speedScale) {
        ModifierContext ctx = buildContext(stack).setCaster(caster);
        Location origin = caster.getEyeLocation();
        Vector aim = origin.getDirection().normalize();
        double speed = Math.max(0.05, ctx.getSpeed() * speedScale);
        int count = Math.max(1, ctx.getCount());

        for (int i = 0; i < count; i++) {
            Vector dir = scatter(aim, ctx.getSpreadDegrees());
            Vector velocity = dir.multiply(speed);
            new FusionProjectile(plugin, burst, ctx, caster.getWorld(),
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
