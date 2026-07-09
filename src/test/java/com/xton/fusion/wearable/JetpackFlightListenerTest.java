package com.xton.fusion.wearable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerToggleFlightEvent;
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
 * {@code AllowFlight} (granted by {@link JetpackTask} to dodge the anti-fly
 * kick) is also what lets the vanilla client's double-tap-space gesture
 * toggle real creative-style flight — this blocks that transition outright
 * for a LIFT wearer, the same treatment {@link JetpackGlideListenerTest}
 * covers for vanilla gliding.
 */
class JetpackFlightListenerTest {

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

    private PlayerMock liftWearer() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(factory.create(Material.ELYTRA, List.of(LiftModifier.ID), "test"));
        return player;
    }

    @Test
    void cancelsToggleIntoRealFlightForLiftWearer() {
        PlayerMock player = liftWearer();

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        new JetpackFlightListener(reader, new WorldFilter(List.of())).onToggleFlight(event);

        assertTrue(event.isCancelled(), "a LIFT wearer's AllowFlight must never turn into real flight");
    }

    @Test
    void leavesNonLiftToggleAlone() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setChestplate(new ItemStack(Material.ELYTRA)); // unfused

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        new JetpackFlightListener(reader, new WorldFilter(List.of())).onToggleFlight(event);

        assertFalse(event.isCancelled(), "no LIFT item worn, nothing to block");
    }

    @Test
    void neverCancelsTurningFlightOff() {
        PlayerMock player = liftWearer();

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, false); // toggling OFF
        new JetpackFlightListener(reader, new WorldFilter(List.of())).onToggleFlight(event);

        assertFalse(event.isCancelled(), "only the transition into real flight is blocked");
    }

    @Test
    void neverCancelsACreativePlayersOwnFlight() {
        PlayerMock player = liftWearer();
        player.setGameMode(GameMode.CREATIVE);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        new JetpackFlightListener(reader, new WorldFilter(List.of())).onToggleFlight(event);

        assertFalse(event.isCancelled(), "a creative player's own legitimate flight must never be blocked");
    }

    @Test
    void neverCancelsASpectatorPlayersOwnFlight() {
        PlayerMock player = liftWearer();
        player.setGameMode(GameMode.SPECTATOR);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        new JetpackFlightListener(reader, new WorldFilter(List.of())).onToggleFlight(event);

        assertFalse(event.isCancelled(), "a spectator player's own legitimate flight must never be blocked");
    }

    @Test
    void doesNotCancelOutsideAllowedWorlds() {
        PlayerMock player = liftWearer();

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        new JetpackFlightListener(reader, new WorldFilter(List.of("some_other_world"))).onToggleFlight(event);

        assertFalse(event.isCancelled(), "outside the allowed worlds LIFT does nothing, including this");
    }
}
