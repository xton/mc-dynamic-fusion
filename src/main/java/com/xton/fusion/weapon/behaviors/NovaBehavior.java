package com.xton.fusion.weapon.behaviors;

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

/**
 * Runs the resolved modifier stack and, if it produced a radial effect, pushes
 * nearby living entities outward from the caster with a particle/sound burst.
 */
public final class NovaBehavior {

    public void execute(Player caster, ModifierStack stack) {
        ModifierContext ctx = new ModifierContext()
                .setCaster(caster)
                .setOrigin(caster.getLocation());
        stack.applyTo(ctx);

        if (!ctx.isRadial()) {
            return;
        }

        World world = caster.getWorld();
        Location origin = ctx.getOrigin();
        double radius = ctx.getRadius();
        double power = ctx.getPower();

        for (Entity entity : world.getNearbyEntities(origin, radius, radius, radius)) {
            if (entity.equals(caster) || !(entity instanceof LivingEntity)) {
                continue;
            }
            Vector push = entity.getLocation().toVector().subtract(origin.toVector());
            if (push.lengthSquared() < 1.0e-6) {
                push = new Vector(0, 1, 0);
            }
            push.normalize().multiply(power);
            push.setY(Math.max(push.getY(), 0.3)); // a little lift so it reads
            entity.setVelocity(entity.getVelocity().add(push));
        }

        Location center = origin.clone().add(0, 1, 0);
        world.spawnParticle(Particle.SWEEP_ATTACK, center, 12, radius / 2, 0.4, radius / 2, 0.0);
        world.spawnParticle(Particle.CLOUD, center, 24, 0.3, 0.3, 0.3, 0.05);
        world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
    }
}
