package com.xton.fusion.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Needs MockBukkit for a real {@link World} to check {@code getName()} against. */
class WorldFilterTest {

    private World overworld;
    private World nether;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        overworld = MockBukkit.getMock().addSimpleWorld("world");
        nether = MockBukkit.getMock().addSimpleWorld("world_nether");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void emptyListAllowsEveryWorld() {
        WorldFilter filter = new WorldFilter(List.of());
        assertTrue(filter.isAllowed(overworld));
        assertTrue(filter.isAllowed(nether));
    }

    @Test
    void nonEmptyListRestrictsToNamedWorlds() {
        WorldFilter filter = new WorldFilter(List.of("world"));
        assertTrue(filter.isAllowed(overworld));
        assertFalse(filter.isAllowed(nether));
    }

    @Test
    void nullWorldIsNeverAllowedWhenRestricted() {
        WorldFilter filter = new WorldFilter(List.of("world"));
        assertFalse(filter.isAllowed(null));
    }

    @Test
    void nullWorldIsAllowedWhenUnrestricted() {
        WorldFilter filter = new WorldFilter(List.of());
        assertTrue(filter.isAllowed(null));
    }
}
