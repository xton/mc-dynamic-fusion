package com.xton.fusion.projectile;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.xton.fusion.modifier.AoeSpec;

/**
 * Payload effect that fires one {@link AoeSpec} entity burst (PUSH/PULL,
 * DAMAGE, or HEAL) at the termination point. A projectile's payload holds one
 * of these per burst emitter in its stack, so a shot can deliver several at
 * once.
 */
public final class BurstEffect implements PayloadEffect {

    private final AoeBurst burst;
    private final AoeSpec spec;

    public BurstEffect(AoeBurst burst, AoeSpec spec) {
        this.burst = burst;
        this.spec = spec;
    }

    @Override
    public void deliver(World world, Location where, Player caster) {
        burst.fire(world, where, spec, caster);
    }
}
