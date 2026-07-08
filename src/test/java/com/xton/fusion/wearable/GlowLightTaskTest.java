package com.xton.fusion.wearable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.xton.fusion.item.FusedItemFactory;
import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.item.FusionKeys;
import com.xton.fusion.item.LoreGenerator;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.GlowModifier;
import com.xton.fusion.util.WorldFilter;

/**
 * GLOW's in-front-of-face light must be client-side only — the real world is
 * never allowed to change, since sendBlockChange was chosen specifically so
 * nothing needs reverting on disconnect/crash/shutdown. The actual placement
 * geometry (sphere search, corner/overhang handling, determinism) lives in
 * {@link LightPlacement} and is exhaustively covered by
 * {@code LightPlacementTest} instead — this file only checks that
 * {@link GlowLightTask} wires it up correctly against a real (mock) world.
 */
class GlowLightTaskTest {

    private ServerMock server;
    private FusedItemReader reader;
    private FusedItemFactory factory;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        FusionKeys keys = new FusionKeys(plugin);
        ModifierRegistry registry = new ModifierRegistry().register(new GlowModifier());
        reader = new FusedItemReader(keys);
        factory = new FusedItemFactory(keys, new LoreGenerator(registry));
        world = server.addSimpleWorld("world");
        world.loadChunk(0, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock glowWearer(Location at) {
        PlayerMock player = server.addPlayer();
        player.setLocation(at);
        player.getInventory().setHelmet(factory.create(Material.DIAMOND_HELMET, List.of(GlowModifier.ID), "test"));
        return player;
    }

    @Test
    void neverWritesToTheRealWorld() {
        // Well above the simple world's generated ground, so the cell ahead is air.
        glowWearer(new Location(world, 0.5, 100, 0.5, 0f, 0f)); // yaw 0 = looking +Z

        Location front = new Location(world, 0, 100, 2, 0f, 0f); // roughly "in front" along +Z
        assertEquals(Material.AIR, front.getBlock().getType(), "sanity: starts as air");

        assertDoesNotThrow(() -> new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).run());

        assertEquals(Material.AIR, front.getBlock().getType(),
                "the real world must never see the LIGHT block — it's client-side only");
    }

    @Test
    void picksStraightAheadWhenClear() {
        PlayerMock player = glowWearer(new Location(world, 0.5, 100, 0.5, 0f, 0f)); // yaw 0 = looking +Z

        Location found = new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).findLightCell(player);

        assertNotNull(found);
        assertEquals(Material.AIR, found.getBlock().getType());
        assertEquals(0, found.getBlockX());
        assertTrue(found.getBlockZ() > 0, "should sit out ahead of the player, not at/behind their feet");
    }

    @Test
    void findsAnOpeningInACornerEvenThoughStraightAheadAndItsReverseAreBothBlocked() {
        // The bug this rewrite fixes: standing in a corner/under an overhang,
        // where the single forward/back line through the eyes is walled off
        // *both* ways, but open air still exists just to the side. A pure 1D
        // ray search (the old algorithm) can never find that; the sphere search
        // must.
        PlayerMock player = glowWearer(new Location(world, 0.5, 100, 0.5, 0f, 0f)); // yaw 0 = looking +Z
        for (int y = 99; y <= 102; y++) {
            new Location(world, 0, y, -1).getBlock().setType(Material.STONE); // behind
            new Location(world, 0, y, 0).getBlock().setType(Material.STONE); // the eyes' own cell
            new Location(world, 0, y, 1).getBlock().setType(Material.STONE); // dead ahead
            // A gap immediately to the side (a generous Y range, so it's in
            // reach whatever the exact eye-height offset) is the only way out.
            new Location(world, 1, y, 0).getBlock().setType(Material.AIR);
        }

        Location found = new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).findLightCell(player);

        assertNotNull(found, "should find the side opening instead of going dark in the corner");
        assertEquals(Material.AIR, found.getBlock().getType());
    }

    @Test
    void cobwebsDontBlockPlacement() {
        // A structure thick with cobwebs (a mineshaft, say) shouldn't starve the
        // search — a cobweb isn't a wall the light would look like it's shining
        // through. Wall off the straight-ahead cell itself with stone so the
        // cobweb, one cell nearer, is the best reachable spot rather than an
        // incidental also-open cell farther out along the same line.
        PlayerMock player = glowWearer(new Location(world, 0.5, 100, 0.5, 0f, 0f)); // yaw 0 = looking +Z
        // A generous Y range on the wall, whatever the exact eye-height offset.
        for (int y = 99; y <= 104; y++) {
            new Location(world, 0, y, 2).getBlock().setType(Material.STONE);
            new Location(world, 0, y, 1).getBlock().setType(Material.COBWEB);
        }

        Location found = new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).findLightCell(player);

        assertNotNull(found);
        assertEquals(Material.COBWEB, found.getBlock().getType(), "a cobweb cell is a perfectly good place to light");
    }

    @Test
    void fullyEmbeddedFindsNothing() {
        PlayerMock player = glowWearer(new Location(world, 0.5, 100, 0.5, 0f, 0f));
        // dy covers well past the eye-height offset in either direction, whatever it is.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    new Location(world, dx, 100 + dy, dz).getBlock().setType(Material.STONE);
                }
            }
        }

        Location found = new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).findLightCell(player);

        assertNull(found, "genuinely embedded — nothing nearby to light");
    }

    @Test
    void nonGlowWearerIsIgnoredWithoutError() {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0.5, 4, 0.5, 0f, 0f));
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET)); // unfused

        assertDoesNotThrow(() -> new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).run());
    }
}
