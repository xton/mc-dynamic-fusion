package com.xton.fusion.wearable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
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

/**
 * Since we block vanilla elytra gliding for LIFT, the jetpack has to grant its
 * own {@code setAllowFlight} exemption or the server's own anti-fly check
 * kicks the player — this covers that grant/revoke bookkeeping (the actual
 * jump/WASD thrust needs a real client's Input packets, so it isn't
 * MockBukkit-testable; see JetpackGlideListenerTest for what is).
 */
class JetpackTaskTest {

    private ServerMock server;
    private FusedItemReader reader;
    private FusedItemFactory factory;
    private JetpackTask task;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        FusionKeys keys = new FusionKeys(plugin);
        ModifierRegistry registry = new ModifierRegistry().register(new LiftModifier());
        reader = new FusedItemReader(keys);
        factory = new FusedItemFactory(keys, new LoreGenerator(registry));
        task = new JetpackTask(reader, 0.1, 0.7, 0.05, 0.6, new WorldFilter(List.of()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock liftWearer(boolean onGround) {
        PlayerMock player = server.addPlayer();
        player.setOnGround(onGround);
        player.getInventory().setChestplate(factory.create(Material.ELYTRA, List.of(LiftModifier.ID), "test"));
        return player;
    }

    @Test
    void grantsFlightWhileAirborneWithLift() {
        PlayerMock player = liftWearer(false);
        assertFalse(player.getAllowFlight(), "sanity: starts without flight");

        task.run();

        assertTrue(player.getAllowFlight(), "airborne LIFT wearer needs the exemption or vanilla kicks them");
    }

    @Test
    void revokesFlightOnceGrounded() {
        PlayerMock player = liftWearer(false);
        task.run();
        assertTrue(player.getAllowFlight());

        player.setOnGround(true);
        task.run();

        assertFalse(player.getAllowFlight(), "landing should give back the exemption we only lent them");
    }

    @Test
    void neverGrantsFlightWithoutLift() {
        PlayerMock player = server.addPlayer();
        player.setOnGround(false);
        player.getInventory().setChestplate(new ItemStack(Material.ELYTRA)); // unfused

        task.run();

        assertFalse(player.getAllowFlight());
    }

    @Test
    void neverRevokesACreativePlayersOwnFlight() {
        PlayerMock player = liftWearer(true); // grounded, not actively jetpacking
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true); // their own legitimate creative flight

        task.run();

        assertTrue(player.getAllowFlight(), "must never strip a creative player's real flight permission");
    }
}
