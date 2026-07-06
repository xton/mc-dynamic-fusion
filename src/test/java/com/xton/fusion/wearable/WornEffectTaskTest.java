package com.xton.fusion.wearable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
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

/** A real (mock) Player wearing GLOW-fused armor should pick up the Glowing effect on a tick. */
class WornEffectTaskTest {

    private ServerMock server;
    private FusedItemReader reader;
    private FusedItemFactory factory;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        FusionKeys keys = new FusionKeys(plugin);
        ModifierRegistry registry = new ModifierRegistry().register(new GlowModifier());
        reader = new FusedItemReader(keys);
        factory = new FusedItemFactory(keys, new LoreGenerator(registry));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void wearingGlowArmorGrantsGlowingEffect() {
        PlayerMock player = server.addPlayer();
        ItemStack helmet = factory.create(Material.DIAMOND_HELMET, List.of(GlowModifier.ID), "test");
        player.getInventory().setHelmet(helmet);

        new WornEffectTask(reader, new WorldFilter(List.of())).run();

        assertTrue(player.hasPotionEffect(PotionEffectType.GLOWING), "wearing GLOW should grant Glowing");
    }

    @Test
    void unfusedArmorGrantsNothing() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));

        new WornEffectTask(reader, new WorldFilter(List.of())).run();

        assertFalse(player.hasPotionEffect(PotionEffectType.GLOWING));
    }
}
