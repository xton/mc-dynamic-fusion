package com.xton.fusion.wearable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * nothing needs reverting on disconnect/crash/shutdown.
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

    @Test
    void neverWritesToTheRealWorld() {
        PlayerMock player = server.addPlayer();
        // Well above the simple world's generated ground, so the cell ahead is air.
        player.setLocation(new Location(world, 0.5, 100, 0.5, 0f, 0f)); // yaw 0 = looking +Z
        player.getInventory().setHelmet(factory.create(Material.DIAMOND_HELMET, List.of(GlowModifier.ID), "test"));

        Location front = new Location(world, 0, 100, 2, 0f, 0f); // roughly "in front" along +Z
        assertEquals(Material.AIR, front.getBlock().getType(), "sanity: starts as air");

        assertDoesNotThrow(() -> new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).run());

        assertEquals(Material.AIR, front.getBlock().getType(),
                "the real world must never see the LIGHT block — it's client-side only");
    }

    @Test
    void relocatesTowardThePlayerWhenFacingAWallUpClose() {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0.5, 100, 0.5, 0f, 0f)); // yaw 0 = looking +Z
        player.getInventory().setHelmet(factory.create(Material.DIAMOND_HELMET, List.of(GlowModifier.ID), "test"));

        // A wall spanning a generous Y range at z=2, well past the default 1.5-block
        // distance whatever the exact eye-height offset — everything closer (z<=1)
        // stays air.
        for (int y = 97; y <= 104; y++) {
            new Location(world, 0, y, 2).getBlock().setType(Material.STONE);
        }

        Location found = new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).findLightCell(player);

        assertNotNull(found, "should back off toward the player and find open air instead of giving up");
        assertEquals(Material.AIR, found.getBlock().getType());
        assertTrue(found.getZ() < 2, "relocated cell should be nearer than the blocked wall");
    }

    @Test
    void continuesPastTheEyesAlongTheSameLineWhenNoseAgainstAWall() {
        PlayerMock player = server.addPlayer();
        // Eyes almost touching the wall (z=1) — even the closest forward sample
        // (MIN_DISTANCE past the eyes) already lands inside it, so the
        // straight-ahead search fails at every forward distance. The fallback
        // must stay on the *same* gaze line rather than hopping sideways —
        // here that lands it right back at the eyes' own cell (z<1).
        player.setLocation(new Location(world, 0.5, 100, 0.75, 0f, 0f)); // yaw 0 = looking +Z
        player.getInventory().setHelmet(factory.create(Material.DIAMOND_HELMET, List.of(GlowModifier.ID), "test"));

        for (int y = 97; y <= 104; y++) {
            new Location(world, 0, y, 1).getBlock().setType(Material.STONE);
            new Location(world, 0, y, 2).getBlock().setType(Material.STONE);
        }

        Location found = new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).findLightCell(player);

        assertNotNull(found, "should keep searching along the gaze line instead of giving up");
        assertEquals(Material.AIR, found.getBlock().getType());
        assertTrue(found.getZ() < 1, "fallback cell should be at/behind the eyes, not inside the wall");
        assertEquals(0.0, found.getX(), 1.0e-9, "must stay on the gaze axis, not hop sideways");
    }

    @Test
    void extendsBehindTheHeadWhenEvenTheEyesOwnCellIsBlocked() {
        PlayerMock player = server.addPlayer();
        // A wall thick enough to cover the eyes' own cell too (z=0,1,2) — the
        // straight-ahead search and the eyes' own cell both fail, so the only
        // way to find air at all is to keep walking the same vector out the
        // back of the player's head into the room behind them (z<0).
        player.setLocation(new Location(world, 0.5, 100, 0.75, 0f, 0f)); // yaw 0 = looking +Z
        player.getInventory().setHelmet(factory.create(Material.DIAMOND_HELMET, List.of(GlowModifier.ID), "test"));
        world.loadChunk(0, -1); // the fallback now reaches z<0, one chunk over from setUp's (0,0)

        for (int y = 97; y <= 104; y++) {
            new Location(world, 0, y, 0).getBlock().setType(Material.STONE);
            new Location(world, 0, y, 1).getBlock().setType(Material.STONE);
            new Location(world, 0, y, 2).getBlock().setType(Material.STONE);
        }

        Location found = new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).findLightCell(player);

        assertNotNull(found, "should keep searching behind the head rather than giving up");
        assertEquals(Material.AIR, found.getBlock().getType());
        assertTrue(found.getZ() < 0, "should have extended past the back of the head into open air");
        assertEquals(0.0, found.getX(), 1.0e-9, "must stay on the gaze axis, not hop sideways");
    }

    @Test
    void nonGlowWearerIsIgnoredWithoutError() {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0.5, 4, 0.5, 0f, 0f));
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET)); // unfused

        assertDoesNotThrow(() -> new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).run());
    }
}
