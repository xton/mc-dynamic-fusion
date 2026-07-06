package com.xton.fusion.wearable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void nonGlowWearerIsIgnoredWithoutError() {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0.5, 4, 0.5, 0f, 0f));
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET)); // unfused

        assertDoesNotThrow(() -> new GlowLightTask(reader, new WorldFilter(List.of()), 1.5).run());
    }
}
