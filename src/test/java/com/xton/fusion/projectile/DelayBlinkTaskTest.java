package com.xton.fusion.projectile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** DELAY's blink is purely cosmetic, but it must self-cancel once its own countdown ends. */
class DelayBlinkTaskTest {

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
    void selfCancelsAfterItsOwnCountdown() {
        Location at = new Location(world, 0.5, 100, 0.5);
        DelayBlinkTask task = new DelayBlinkTask(world, at, 5);
        task.runTaskTimer(plugin, 0L, 1L);

        assertFalse(task.isCancelled());
        server.getScheduler().performTicks(10);
        assertTrue(task.isCancelled(), "should self-cancel once its countdown ends, not run forever");
    }
}
