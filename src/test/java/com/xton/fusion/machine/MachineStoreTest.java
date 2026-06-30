package com.xton.fusion.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Needs MockBukkit for a World to build Locations against. */
class MachineStoreTest {

    private static final Logger LOG = Logger.getLogger("test");

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void addContainsRemove(@TempDir Path dir) {
        File file = dir.resolve("machines.yml").toFile();
        MachineStore store = new MachineStore(file, LOG);
        Location loc = new Location(world, 10, 64, -20);

        assertFalse(store.contains(loc));
        store.add(loc);
        assertTrue(store.contains(loc));
        assertEquals(1, store.size());

        store.remove(loc);
        assertFalse(store.contains(loc));
    }

    @Test
    void persistsAcrossReload(@TempDir Path dir) {
        File file = dir.resolve("machines.yml").toFile();
        Location loc = new Location(world, 1, 2, 3);

        new MachineStore(file, LOG).add(loc);

        // A fresh store reading the same file should see the machine.
        MachineStore reloaded = new MachineStore(file, LOG);
        assertTrue(reloaded.contains(loc));
        assertEquals(1, reloaded.size());
    }
}
