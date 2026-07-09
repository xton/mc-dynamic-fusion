package com.xton.fusion.wearable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Zombie;
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
 * Worn armor auras fire through the same anchor-projectile pipeline a real
 * weapon's shot uses (see {@link ProjectileLauncher#launchAnchored}), so
 * these check the wiring end to end (a pulse really does ignite something
 * nearby) rather than re-testing the environmental sweep itself. A worn
 * FIRE-aura piece must also keep its wearer topped up on Fire Resistance —
 * otherwise the real fire blocks the aura drops underfoot would burn the very
 * person wearing it, contradicting the "immune to that damage" ask. ICE's
 * aura never creates a real hazard block, so it needs no equivalent.
 */
class WornAuraTaskTest {

    private ServerMock server;
    private World world;
    private FusedItemReader reader;
    private FusedItemFactory factory;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private WornAuraTask newTask(int periodTicks, double distanceBlocks) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        Plugin plugin = MockBukkit.createMockPlugin();
        FusionKeys keys = new FusionKeys(plugin);
        ModifierRegistry registry = new ModifierRegistry()
                .register(new FireModifier())
                .register(new IceModifier());
        reader = new FusedItemReader(keys);
        factory = new FusedItemFactory(keys, new LoreGenerator(registry));

        AoeBurst burst = new AoeBurst(null, new AoeBurst.Settings(6.0, 20, false));
        ProjectileLauncher launcher = new ProjectileLauncher(plugin, burst,
                new com.xton.fusion.modifier.WeaponBuilder.Defaults(
                        1.6, 30, 3.0, 2.0, 1.0, 2.5, 4.0, 1.5, 1.5, 1.5, 1.5),
                new EnvironmentalAoe.Settings(100, 140, 8.0, 100.0, 100),
                new BounceSettings(0.55, 0.5, 0.05),
                new PotionCloud.Settings(6000, 60, 0), 2, 1, 4.0);
        return new WornAuraTask(reader, registry, launcher, new WorldFilter(List.of()), periodTicks, distanceBlocks);
    }

    private PlayerMock wearer(Location at, String modifierId) {
        PlayerMock player = server.addPlayer();
        player.setLocation(at);
        player.getInventory().setChestplate(factory.create(Material.DIAMOND_CHESTPLATE, List.of(modifierId), "test"));
        return player;
    }

    @Test
    void fireAuraArmorGrantsFireResistance() {
        WornAuraTask task = newTask(20, 2.0);
        PlayerMock player = wearer(new Location(world, 0.5, 100, 0.5), FireModifier.ID);

        task.run();

        assertTrue(player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE),
                "wearing a FIRE aura must keep its own wearer safe from the fire it drops");
    }

    @Test
    void iceAuraArmorGrantsNoFireResistance() {
        WornAuraTask task = newTask(20, 2.0);
        PlayerMock player = wearer(new Location(world, 0.5, 100, 0.5), IceModifier.ID);

        task.run();

        assertFalse(player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE), "ICE has no fire-immunity need");
    }

    @Test
    void unfusedArmorDoesNothing() {
        WornAuraTask task = newTask(20, 2.0);
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));

        task.run();

        assertFalse(player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE));
    }

    @Test
    void aPulseActuallyIgnitesANearbyMob() {
        // First sighting of a FIRE-aura wearer is always due, so this exercises
        // the full anchor pipeline: launchAnchored schedules a real, zero-velocity
        // FusionProjectile rooted at the wearer that detonates on the very next
        // tick (zero duration) and sweeps FIRE onto whatever's nearby.
        WornAuraTask task = newTask(20, 2.0);
        PlayerMock player = wearer(new Location(world, 0.5, 100, 0.5), FireModifier.ID);
        Zombie mob = world.spawn(new Location(world, 1.0, 100, 0.5), Zombie.class);

        task.run();
        server.getScheduler().performTicks(2);

        assertTrue(mob.getFireTicks() > 0, "the aura's anchor should have ignited the nearby mob");
    }

    @Test
    void walkingFarEnoughForcesAnEarlyPulse() {
        // A huge period means only the distance trigger can be responsible for
        // the second pulse.
        WornAuraTask task = newTask(20_000, 2.0);
        PlayerMock player = wearer(new Location(world, 0.5, 100, 0.5), FireModifier.ID);
        task.run();
        server.getScheduler().performTicks(2);

        // Walk far enough to cross the distance threshold, then check a mob
        // near the *new* spot: only a fresh pulse rooted there would reach it.
        player.setLocation(new Location(world, 10.5, 100, 0.5));
        Zombie mob = world.spawn(new Location(world, 11.0, 100, 0.5), Zombie.class);
        task.run();
        server.getScheduler().performTicks(2);

        assertTrue(mob.getFireTicks() > 0, "walking past the distance threshold should force an early pulse");
    }
}
