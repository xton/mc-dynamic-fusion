package com.xton.fusion.projectile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** TELEPORT's dash: invulnerable for the transit, lands exactly at the destination, velocity zeroed. */
class TeleportZoomTaskTest {

    private ServerMock server;
    private Plugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void grantsInvulnerabilityDuringTheZoomAndRevokesAfter() {
        PlayerMock player = server.addPlayer();
        player.setInvulnerable(false);
        Location from = new Location(world, 0.5, 100, 0.5);
        Location to = new Location(world, 0.5, 100, 10.5);
        player.teleport(from);
        player.setVelocity(new Vector(0, 5, 0));

        new TeleportZoomTask(player, from, to, 6).runTaskTimer(plugin, 0L, 1L);

        server.getScheduler().performTicks(1);
        assertTrue(player.isInvulnerable(), "invulnerable for the transit");

        server.getScheduler().performTicks(10); // well past totalTicks=6

        assertFalse(player.isInvulnerable(), "restored once the dash lands");
        assertEquals(to.getX(), player.getLocation().getX(), 1.0e-6);
        assertEquals(to.getZ(), player.getLocation().getZ(), 1.0e-6);
        assertEquals(0.0, player.getVelocity().length(), 1.0e-6, "landing zeroes velocity");
    }

    @Test
    void neverStripsAPreExistingInvulnerability() {
        PlayerMock player = server.addPlayer();
        player.setInvulnerable(true); // e.g. creative mode
        Location from = new Location(world, 0.5, 100, 0.5);
        Location to = new Location(world, 0.5, 100, 3.5);
        player.teleport(from);

        new TeleportZoomTask(player, from, to, 3).runTaskTimer(plugin, 0L, 1L);
        server.getScheduler().performTicks(10);

        assertTrue(player.isInvulnerable(), "must not turn off an invulnerability we didn't grant");
    }

    @Test
    void survivesOverlappingZoomsWithoutGettingStuckInvulnerable() {
        // Regression: two separate casts (not the same Shot, so the per-Shot
        // teleport latch doesn't guard against this) land close enough
        // together that their zooms overlap. The second task used to capture
        // "invulnerable" (the first task's own grant) as if it were the real
        // original, then restore *that* when it finished last — leaving the
        // player stuck invulnerable forever with nothing left to undo it.
        PlayerMock player = server.addPlayer();
        player.setInvulnerable(false);
        Location from = new Location(world, 0.5, 100, 0.5);
        Location toA = new Location(world, 0.5, 100, 6.5);
        Location toB = new Location(world, 0.5, 100, 12.5);
        player.teleport(from);

        new TeleportZoomTask(player, from, toA, 6).runTaskTimer(plugin, 0L, 1L);
        server.getScheduler().performTicks(2);
        new TeleportZoomTask(player, from, toB, 6).runTaskTimer(plugin, 0L, 1L); // starts mid-flight of the first

        server.getScheduler().performTicks(4); // task A's timer (6 ticks) has now elapsed; B still has 2 to go
        assertTrue(player.isInvulnerable(), "still invulnerable — a second zoom is still in flight");

        server.getScheduler().performTicks(4); // task B's timer has now elapsed too
        assertFalse(player.isInvulnerable(), "must not be stuck invulnerable once every zoom has landed");
    }
}
