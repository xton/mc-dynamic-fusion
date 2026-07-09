package com.xton.fusion.projectile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;

/**
 * DEPOSIT:LAVA/WATER place real source blocks, which vanilla physics would
 * otherwise let spread indefinitely (including back toward the caster) — so
 * each one has to clean itself up after a while instead of lingering forever.
 */
class EnvironmentalAoeTest {

    private ServerMock server;
    private Plugin plugin;
    private World world;
    private Player caster;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        caster = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnvironmentalAoe.Settings settings(int fluidRevertTicks) {
        return new EnvironmentalAoe.Settings(100, 140, 8.0, 100.0, fluidRevertTicks);
    }

    @Test
    void depositedLavaRevertsToAirAfterItsTimer() {
        EnvironmentalAoe env = new EnvironmentalAoe(plugin, world, caster, settings(5));
        Location at = new Location(world, 0.5, 100, 0.5);
        AoeSpec deposit = new AoeSpec(AoeKind.DEPOSIT, 0.4, 0, Material.LAVA);

        env.applyBlocks(deposit, at);
        assertEquals(Material.LAVA, at.getBlock().getType(), "placed immediately, as before");

        server.getScheduler().performTicks(5);
        assertEquals(Material.AIR, at.getBlock().getType(),
                "a deposited fluid must self-clean so it can't flow forever");
    }

    @Test
    void nonFluidDepositStaysPermanent() {
        EnvironmentalAoe env = new EnvironmentalAoe(plugin, world, caster, settings(5));
        Location at = new Location(world, 0.5, 100, 0.5);
        AoeSpec deposit = new AoeSpec(AoeKind.DEPOSIT, 0.4, 0, Material.DIRT);

        env.applyBlocks(deposit, at);
        server.getScheduler().performTicks(20);

        assertEquals(Material.DIRT, at.getBlock().getType(), "solid deposits don't flow, so they stay put");
    }

    @Test
    void reburiedFluidCellIsLeftAlone() {
        // If the source cell got overwritten (e.g. re-swept) before its timer
        // fires, the stale revert must not clobber whatever's there now.
        EnvironmentalAoe env = new EnvironmentalAoe(plugin, world, caster, settings(5));
        Location at = new Location(world, 0.5, 100, 0.5);
        AoeSpec deposit = new AoeSpec(AoeKind.DEPOSIT, 0.4, 0, Material.LAVA);

        env.applyBlocks(deposit, at);
        at.getBlock().setType(Material.STONE, true);
        server.getScheduler().performTicks(5);

        assertEquals(Material.STONE, at.getBlock().getType(), "revert must not stomp a block that changed since");
    }
}
