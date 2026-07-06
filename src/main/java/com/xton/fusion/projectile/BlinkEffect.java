package com.xton.fusion.projectile;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * The "armed and waiting" visual shared by DETECT mines and DELAY charges: a
 * slow on/off red pulse so a stationary, about-to-go-off child reads as
 * watching/counting down, not just sitting inert.
 */
final class BlinkEffect {

    /** Ticks per on/off half-cycle. */
    private static final int PERIOD_TICKS = 10;
    /** Warning-light red. */
    private static final Particle.DustOptions DUST = new Particle.DustOptions(Color.fromRGB(255, 70, 40), 1.2f);

    private BlinkEffect() {
    }

    /** Spawn the pulse at {@code at} if {@code age} falls in the "on" half of the blink cycle. */
    static void tick(World world, Location at, int age) {
        if ((age / PERIOD_TICKS) % 2 == 0) {
            world.spawnParticle(Particle.DUST, at.clone().add(0, 0.25, 0), 1, 0.05, 0.05, 0.05, 0.0, DUST);
        }
    }
}
