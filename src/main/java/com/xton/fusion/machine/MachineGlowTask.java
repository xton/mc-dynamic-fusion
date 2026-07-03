package com.xton.fusion.machine;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

/**
 * Ambient glow above placed Fusion Machines, so one stands out from a plain
 * enchanting table. Machines are marked only by a block-entity PDC flag (no
 * location registry), so each period we scan the tile-entities in the chunks
 * around each online player — cheap, since a chunk holds few block-entities —
 * and spawn a subtle particle column above any tagged one. Cosmetic; config-gated.
 */
public final class MachineGlowTask implements Runnable {

    /** Chunks around each player to scan for machines. */
    private static final int CHUNK_RADIUS = 2;

    private final FusionMachineMenu menu;

    public MachineGlowTask(FusionMachineMenu menu) {
        this.menu = menu;
    }

    @Override
    public void run() {
        Set<Long> glowed = new HashSet<>(); // don't double-glow a machine two players both see
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            int pcx = player.getLocation().getBlockX() >> 4;
            int pcz = player.getLocation().getBlockZ() >> 4;
            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    if (!world.isChunkLoaded(cx, cz)) {
                        continue;
                    }
                    Chunk chunk = world.getChunkAt(cx, cz);
                    for (BlockState state : chunk.getTileEntities(false)) {
                        if (!menu.isMachineState(state)) {
                            continue;
                        }
                        Location loc = state.getLocation();
                        if (glowed.add(key(loc))) {
                            glow(world, loc);
                        }
                    }
                }
            }
        }
    }

    private void glow(World world, Location block) {
        Location above = block.clone().add(0.5, 1.1, 0.5);
        world.spawnParticle(Particle.END_ROD, above, 3, 0.12, 0.16, 0.12, 0.01);
        world.spawnParticle(Particle.ENCHANT, above, 6, 0.2, 0.2, 0.2, 0.4);
    }

    private static long key(Location loc) {
        return ((long) (loc.getBlockX() & 0x3FFFFFF) << 38)
                | ((long) (loc.getBlockZ() & 0x3FFFFFF) << 12)
                | (loc.getBlockY() & 0xFFF);
    }
}
