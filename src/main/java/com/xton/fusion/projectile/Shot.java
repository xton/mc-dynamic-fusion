package com.xton.fusion.projectile;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;

/**
 * Per-cast context threaded through every projectile of a shot — including the
 * children a SPAWN emitter launches. Carries the caster, the current spawn
 * depth (generation), the environmental tunables, a back-reference to the
 * launcher (so a projectile can spawn its children), and the shared TELEPORT
 * latch that makes a cast teleport at most once.
 */
public record Shot(Player caster, int generation, int maxSpawnGeneration,
                   EnvironmentalAoe.Settings env, ProjectileLauncher launcher,
                   AtomicBoolean teleportLatch) {

    /** Whether a child may still spawn at this depth. */
    public boolean canSpawn() {
        return launcher != null && generation < maxSpawnGeneration;
    }

    /** The context one generation deeper, for a spawned child. */
    public Shot deeper() {
        return new Shot(caster, generation + 1, maxSpawnGeneration, env, launcher, teleportLatch);
    }
}
