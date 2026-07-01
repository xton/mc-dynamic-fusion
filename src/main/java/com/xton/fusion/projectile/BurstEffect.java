package com.xton.fusion.projectile;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.xton.fusion.modifier.ModifierContext;

/**
 * Payload effect that fires the AOE {@link AoeBurst} at the termination point.
 * Attached to a projectile's payload only when the stack opted a burst in
 * ({@link ModifierContext#hasBurst()}).
 */
public final class BurstEffect implements PayloadEffect {

    private final AoeBurst burst;
    private final ModifierContext ctx;

    public BurstEffect(AoeBurst burst, ModifierContext ctx) {
        this.burst = burst;
        this.ctx = ctx;
    }

    @Override
    public void deliver(World world, Location where, Player caster, int generation) {
        burst.fire(world, where, ctx, caster);
    }
}
