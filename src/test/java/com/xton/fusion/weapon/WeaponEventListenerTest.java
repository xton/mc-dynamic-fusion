package com.xton.fusion.weapon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
import com.xton.fusion.modifier.WeaponBuilder;
import com.xton.fusion.modifier.impl.PushModifier;
import com.xton.fusion.projectile.AoeBurst;
import com.xton.fusion.projectile.BounceSettings;
import com.xton.fusion.projectile.EnvironmentalAoe;
import com.xton.fusion.projectile.PotionCloud;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.util.WorldFilter;

/**
 * Right-clicking an entity (trading with a villager, mounting a horse, ...)
 * fires its own arm-swing animation, distinct from the block/air right-click
 * {@link WeaponEventListener} already filters — this covers that it doesn't
 * slip past and get misread as a real attack swing.
 */
class WeaponEventListenerTest {

    private ServerMock server;
    private FusedItemFactory factory;
    private CooldownMap cooldown;
    private WeaponEventListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        FusionKeys keys = new FusionKeys(plugin);
        ModifierRegistry registry = new ModifierRegistry().register(new PushModifier());
        FusedItemReader reader = new FusedItemReader(keys);
        factory = new FusedItemFactory(keys, new LoreGenerator(registry));

        AoeBurst burst = new AoeBurst(null, new AoeBurst.Settings(6.0, 20, false));
        ProjectileLauncher launcher = new ProjectileLauncher(plugin, burst,
                new WeaponBuilder.Defaults(1.6, 30, 3.0, 2.0, 1.0, 2.5, 4.0, 1.5, 1.5, 1.5, 1.5),
                new EnvironmentalAoe.Settings(100, 140, 8.0, 100.0, 100),
                new BounceSettings(0.55, 0.5, 0.05),
                new PotionCloud.Settings(6000, 60, 0), 2, 1, 4.0);
        cooldown = new CooldownMap(60_000); // long window so a fired weapon's cooldown clearly still holds
        listener = new WeaponEventListener(reader, registry, launcher, cooldown, new WorldFilter(List.of()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock wielder() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(factory.create(Material.IRON_SWORD, List.of(PushModifier.ID), "test"));
        return player;
    }

    @Test
    void rightClickingAnEntityDoesNotFireTheHeldWeapon() {
        PlayerMock player = wielder();
        PlayerMock target = server.addPlayer(); // stands in for a villager/any entity

        listener.onInteractEntity(new PlayerInteractEntityEvent(player, target));
        listener.onSwing(new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING));

        assertTrue(cooldown.tryUse(player.getUniqueId()),
                "the cooldown must be untouched — the swing that followed a right-click-on-entity shouldn't fire");
    }

    @Test
    void aGenuineSwingStillFiresTheHeldWeapon() {
        PlayerMock player = wielder();

        listener.onSwing(new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING));

        assertFalse(cooldown.tryUse(player.getUniqueId()),
                "a real swing (no preceding right-click) must fire and consume the cooldown");
    }
}
