package com.xton.fusion.projectile;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * One effect a projectile delivers where it terminates. The projectile's
 * {@link Payload} is a list of these; delivering an empty list is a no-op (a
 * mining ray, for instance, delivers nothing at its terminus).
 *
 * <p>Today the only effect is {@link BurstEffect}. The seam is deliberate: a
 * future spawn effect would re-launch child projectiles from {@code where}
 * (carrying a decremented {@code generation} so it terminates) — that is how a
 * cluster bomb is built, without any special-casing elsewhere.
 */
@FunctionalInterface
public interface PayloadEffect {

    /**
     * Deliver this effect.
     *
     * @param world      the world the projectile ended in
     * @param where      the termination point
     * @param caster     the wielder (excluded from self-hits), may be null
     * @param generation projectile depth, for spawn effects to bound recursion
     */
    void deliver(World world, Location where, Player caster, int generation);
}
