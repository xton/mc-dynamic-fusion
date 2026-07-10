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
 * The jetpack never touches {@code AllowFlight}/{@code isFlying} at all — the
 * server's anti-fly kick is silenced server-wide via {@code server.properties}'
 * {@code allow-flight} (see {@link JetpackTask}'s class doc), so there's no
 * per-player permission bookkeeping left to do here. This just guards that
 * invariant (the actual jump/WASD thrust needs a real client's Input packets,
 * so it isn't MockBukkit-testable; see JetpackGlideListenerTest for what is).
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
    void neverGrantsFlightToAnAirborneLiftWearer() {
        PlayerMock player = liftWearer(false);

        task.run();

        assertFalse(player.getAllowFlight(),
                "the jetpack relies on server.properties allow-flight, not a per-player grant");
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
    void neverTouchesACreativePlayersOwnFlight() {
        PlayerMock player = liftWearer(false);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true); // their own legitimate creative flight

        task.run();

        assertTrue(player.getAllowFlight(), "must never strip a creative player's real flight permission");
    }
}
