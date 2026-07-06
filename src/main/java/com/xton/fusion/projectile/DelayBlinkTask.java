package com.xton.fusion.projectile;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Purely cosmetic: blinks in place ({@link BlinkEffect}) for the ticks a
 * DELAY child is waiting before it detonates, so the wait reads as an armed
 * charge counting down rather than nothing happening. The actual detonation
 * timing is untouched — this only self-cancels once its own countdown ends.
 */
final class DelayBlinkTask extends BukkitRunnable {

    private final World world;
    private final Location at;
    private final int totalTicks;
    private int age;

    DelayBlinkTask(World world, Location at, int totalTicks) {
        this.world = world;
        this.at = at;
        this.totalTicks = totalTicks;
    }

    @Override
    public void run() {
        if (age >= totalTicks) {
            cancel();
            return;
        }
        BlinkEffect.tick(world, at, age);
        age++;
    }
}
