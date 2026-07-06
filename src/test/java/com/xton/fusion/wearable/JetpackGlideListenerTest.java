package com.xton.fusion.wearable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.event.entity.EntityToggleGlideEvent;
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
import com.xton.fusion.modifier.impl.LiftModifier;
import com.xton.fusion.util.WorldFilter;

/** A fused LIFT chestplate/elytra blocks the vanilla elytra glide from ever engaging. */
class JetpackGlideListenerTest {

    private ServerMock server;
    private FusedItemReader reader;
    private FusedItemFactory factory;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        FusionKeys keys = new FusionKeys(plugin);
        ModifierRegistry registry = new ModifierRegistry().register(new LiftModifier());
        reader = new FusedItemReader(keys);
        factory = new FusedItemFactory(keys, new LoreGenerator(registry));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cancelsGlideForLiftWearer() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(factory.create(Material.ELYTRA, List.of(LiftModifier.ID), "test"));

        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, true);
        new JetpackGlideListener(reader, new WorldFilter(List.of())).onToggleGlide(event);

        assertTrue(event.isCancelled(), "LIFT wearer should never enter vanilla gliding");
    }

    @Test
    void leavesNonLiftGlideAlone() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(new ItemStack(Material.ELYTRA)); // unfused

        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, true);
        new JetpackGlideListener(reader, new WorldFilter(List.of())).onToggleGlide(event);

        assertFalse(event.isCancelled(), "a plain elytra glides exactly as vanilla");
    }

    @Test
    void neverCancelsTurningGlideOff() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(factory.create(Material.ELYTRA, List.of(LiftModifier.ID), "test"));

        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, false); // toggling OFF
        new JetpackGlideListener(reader, new WorldFilter(List.of())).onToggleGlide(event);

        assertFalse(event.isCancelled(), "only the transition into gliding is blocked");
    }

    @Test
    void doesNotCancelOutsideAllowedWorlds() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(factory.create(Material.ELYTRA, List.of(LiftModifier.ID), "test"));

        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, true);
        new JetpackGlideListener(reader, new WorldFilter(List.of("some_other_world"))).onToggleGlide(event);

        assertFalse(event.isCancelled(), "outside the allowed worlds LIFT does nothing, including this");
    }
}
