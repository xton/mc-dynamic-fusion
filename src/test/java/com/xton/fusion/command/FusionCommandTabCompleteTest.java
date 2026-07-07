package com.xton.fusion.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.xton.fusion.item.LatentRegistry;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.MobModifier;
import com.xton.fusion.modifier.impl.PotionModifier;
import com.xton.fusion.modifier.impl.PushModifier;

/**
 * Once a {@code /fusion give} argument has a colon, completion should switch
 * from the bare modifier-ID list to that modifier's own parameter candidates
 * (or, for {@code from:}, the latent registry's known materials) — otherwise
 * suggestions vanish right when they're most useful.
 */
class FusionCommandTabCompleteTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private FusionCommand command(ModifierRegistry registry, LatentRegistry latent) {
        return new FusionCommand(null, registry, latent, null, null, 0, Logger.getLogger("test"), false, null);
    }

    @Test
    void suggestsMobEntityTypesAfterTheColon() {
        FusionCommand cmd = command(new ModifierRegistry().register(new MobModifier()), null);
        PlayerMock player = server.addPlayer();
        List<String> out = cmd.onTabComplete(player, null, null,
                new String[] {"give", player.getName(), "STICK", "MOB:CO"});
        assertTrue(out.contains("MOB:COW"), "expected MOB:COW, got " + out);
    }

    // DepositModifier's own parameterCompletions (Material.values().isBlock()) isn't
    // covered here — MockBukkit doesn't implement legacy-material conversion for
    // every Material, so iterating the full enum errors under the test harness
    // even though the identical check already works in DepositModifier.resolveMaterial()
    // on a real server. Covered by manual UAT instead.

    @Test
    void suggestsPotionEffectsAfterTheColon() {
        FusionCommand cmd = command(new ModifierRegistry().register(new PotionModifier()), null);
        PlayerMock player = server.addPlayer();
        List<String> out = cmd.onTabComplete(player, null, null,
                new String[] {"give", player.getName(), "STICK", "POTION:POI"});
        assertTrue(out.contains("POTION:POISON"), "expected POTION:POISON, got " + out);
    }

    @Test
    void suggestsLatentMaterialsAfterFromColon() {
        LatentRegistry latent = new LatentRegistry(Map.of(Material.NETHER_STAR, List.of("PUSH")));
        FusionCommand cmd = command(new ModifierRegistry().register(new PushModifier()), latent);
        PlayerMock player = server.addPlayer();
        List<String> out = cmd.onTabComplete(player, null, null,
                new String[] {"give", player.getName(), "STICK", "from:NETHER_S"});
        assertTrue(out.contains("from:NETHER_STAR"), "expected from:NETHER_STAR, got " + out);
    }

    @Test
    void suggestsFromAlongsideModifierIdsBeforeAnyColon() {
        FusionCommand cmd = command(new ModifierRegistry().register(new PushModifier()), null);
        PlayerMock player = server.addPlayer();
        List<String> out = cmd.onTabComplete(player, null, null,
                new String[] {"give", player.getName(), "STICK", "FR"});
        assertTrue(out.contains("FROM"), "expected FROM to be offered as a base completion, got " + out);
    }
}
