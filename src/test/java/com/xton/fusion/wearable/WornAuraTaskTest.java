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
import com.xton.fusion.modifier.impl.FireModifier;
import com.xton.fusion.modifier.impl.IceModifier;
import com.xton.fusion.projectile.AoeBurst;
import com.xton.fusion.projectile.BounceSettings;
import com.xton.fusion.projectile.EnvironmentalAoe;
import com.xton.fusion.projectile.PotionCloud;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.util.WorldFilter;

/**
 * A worn FIRE-aura armor piece must keep its wearer topped up on Fire
 * Resistance — otherwise the real fire blocks the aura drops underfoot would
 * burn the very person wearing it, contradicting the "immune to that damage"
 * ask. ICE's aura never creates a real hazard block, so it needs no
 * equivalent.
 */
class WornAuraTaskTest {

    private ServerMock server;
    private FusedItemReader reader;
    private FusedItemFactory factory;
    private WornAuraTask task;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        FusionKeys keys = new FusionKeys(plugin);
        ModifierRegistry registry = new ModifierRegistry()
                .register(new FireModifier())
                .register(new IceModifier());
        reader = new FusedItemReader(keys);
        factory = new FusedItemFactory(keys, new LoreGenerator(registry));

        AoeBurst burst = new AoeBurst(null, new AoeBurst.Settings(6.0, 20, false));
        EnvironmentalAoe.Settings envSettings = new EnvironmentalAoe.Settings(100, 140, 8.0, 100.0, 100);
        ProjectileLauncher launcher = new ProjectileLauncher(plugin, burst,
                new com.xton.fusion.modifier.WeaponBuilder.Defaults(
                        1.6, 30, 3.0, 2.0, 1.0, 2.5, 4.0, 1.5, 1.5, 1.5, 1.5),
                envSettings, new BounceSettings(0.55, 0.5, 0.05),
                new PotionCloud.Settings(6000, 60, 0), 2, 1, 4.0);
        task = new WornAuraTask(reader, registry, launcher, envSettings, new WorldFilter(List.of()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock wearer(Material armorPiece, String modifierId) {
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(factory.create(armorPiece, List.of(modifierId), "test"));
        return player;
    }

    @Test
    void fireAuraArmorGrantsFireResistance() {
        PlayerMock player = wearer(Material.DIAMOND_CHESTPLATE, FireModifier.ID);

        task.run();

        assertTrue(player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE),
                "wearing a FIRE aura must keep its own wearer safe from the fire it drops");
    }

    @Test
    void iceAuraArmorGrantsNoFireResistance() {
        PlayerMock player = wearer(Material.DIAMOND_CHESTPLATE, IceModifier.ID);

        task.run();

        assertFalse(player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE), "ICE has no fire-immunity need");
    }

    @Test
    void unfusedArmorDoesNothing() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));

        task.run();

        assertFalse(player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE));
    }
}
