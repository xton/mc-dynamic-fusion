package com.xton.fusion.projectile;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * One effect a projectile delivers where it terminates. The projectile's
 * {@link Payload} is a list of these; delivering an empty list is a no-op (a
 * mining ray, for instance, delivers nothing at its terminus).
 *
 * <p>Today the only effect is {@link BurstEffect}, which fires an entity burst.
 * Cluster/spawn behavior is <em>not</em> a payload effect — SPAWN children live
 * on {@link com.xton.fusion.modifier.ProjectileSpec#spawns()} and are launched
 * by the projectile at its terminus, with recursion bounded by the cast's
 * {@link Shot}.
 */
@FunctionalInterface
public interface PayloadEffect {

    /**
     * Deliver this effect.
     *
     * @param world  the world the projectile ended in
     * @param where  the termination point
     * @param caster the wielder (excluded from self-hits), may be null
     */
    void deliver(World world, Location where, Player caster);
}
