package com.xton.fusion.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.item.FusedItemFactory;
import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.item.FusionKeys;
import com.xton.fusion.item.LatentRegistry;
import com.xton.fusion.item.LoreGenerator;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.PushModifier;

/**
 * Only the layout/tracking is unit-tested here, same boundary as the anvil
 * {@code FusionMachineMenu}: simulating real click/take/close interactions
 * needs a live client, per the manual UAT checklist.
 */
class FusionChestMenuTest {

    private ServerMock server;
    private Plugin plugin;
    private FusionEngine engine;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        FusionKeys keys = new FusionKeys(plugin);
        ModifierRegistry registry = new ModifierRegistry().register(new PushModifier());
        FusedItemReader reader = new FusedItemReader(keys);
        FusedItemFactory factory = new FusedItemFactory(keys, new LoreGenerator(registry));
        LatentRegistry latent = new LatentRegistry(Map.of(Material.NETHER_STAR, List.of("PUSH")));
        engine = new FusionEngine(latent, reader, factory, 8);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openLaysOutArrowAndFillerAroundEmptyInputAndResultSlots() {
        FusionChestMenu chestMenu = new FusionChestMenu(plugin, engine, 0, plugin.getLogger(), false);
        PlayerMock player = server.addPlayer();
        World world = server.addSimpleWorld("world");

        chestMenu.open(player, new Location(world, 0, 0, 0));

        Inventory top = player.getOpenInventory().getTopInventory();
        assertEquals(9, top.getSize());
        assertNull(top.getItem(0), "target slot starts empty");
        assertNull(top.getItem(1), "ingredient slot starts empty");
        assertEquals(Material.ARROW, top.getItem(2).getType());
        assertEquals(Material.LIGHT_GRAY_STAINED_GLASS_PANE, top.getItem(3).getType(),
                "result slot is a placeholder, not truly empty, so shift-click can't land there");
        for (int i = 4; i < 9; i++) {
            assertEquals(Material.GRAY_STAINED_GLASS_PANE, top.getItem(i).getType());
        }
    }

    @Test
    void tracksWhoHasTheChestOpen() {
        FusionChestMenu chestMenu = new FusionChestMenu(plugin, engine, 0, plugin.getLogger(), false);
        PlayerMock player = server.addPlayer();
        World world = server.addSimpleWorld("world");

        assertFalse(chestMenu.isOpenFor(player));
        chestMenu.open(player, new Location(world, 0, 0, 0));
        assertTrue(chestMenu.isOpenFor(player));
    }

    @Test
    void closeGivesBackUncollectedTargetAndIngredient() {
        FusionChestMenu chestMenu = new FusionChestMenu(plugin, engine, 0, plugin.getLogger(), false);
        PlayerMock player = server.addPlayer();
        World world = server.addSimpleWorld("world");
        chestMenu.open(player, new Location(world, 0, 0, 0));

        Inventory top = player.getOpenInventory().getTopInventory();
        top.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        top.setItem(1, new ItemStack(Material.NETHER_STAR));

        chestMenu.onClose(new org.bukkit.event.inventory.InventoryCloseEvent(player.getOpenInventory()));

        assertTrue(player.getInventory().contains(Material.DIAMOND_SWORD));
        assertTrue(player.getInventory().contains(Material.NETHER_STAR));
        assertFalse(chestMenu.isOpenFor(player));
    }
}
