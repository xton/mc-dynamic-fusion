package com.xton.fusion.machine;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Ambient glow above each placed Fusion Machine so they're easy to spot. Runs
 * on a repeating tick; only touches machines in loaded chunks.
 */
public final class MachineGlowTask implements Runnable {

    private final MachineStore store;

    public MachineGlowTask(MachineStore store) {
        this.store = store;
    }

    @Override
    public void run() {
        for (Location loc : store.locations()) {
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            world.spawnParticle(Particle.ENCHANT, loc.clone().add(0.5, 1.1, 0.5),
                    6, 0.25, 0.2, 0.25, 0.02);
        }
    }
}
