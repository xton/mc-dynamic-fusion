package com.xton.fusion.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** Pure: Material is an enum and the map constructor needs no server. */
class LatentRegistryTest {

    @Test
    void returnsAssignedModifiers() {
        LatentRegistry registry = new LatentRegistry(
                Map.of(Material.NETHER_STAR, List.of("NOVA")));

        assertEquals(List.of("NOVA"), registry.get(Material.NETHER_STAR));
        assertTrue(registry.has(Material.NETHER_STAR));
    }

    @Test
    void unassignedMaterialsContributeNothing() {
        LatentRegistry registry = new LatentRegistry(
                Map.of(Material.NETHER_STAR, List.of("NOVA")));

        assertEquals(List.of(), registry.get(Material.DIRT));
        assertFalse(registry.has(Material.DIRT));
    }
}
