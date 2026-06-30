package com.xton.fusion.weapon.behaviors;

import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Mining ray: on swing, sweeps rays across an arc in the look direction and
 * breaks the first block each ray hits, as long as it's soft enough.
 *
 * <p>Respects hardness up to a configurable cap (so obsidian/bedrock resist),
 * skips unbreakable blocks, and uses {@code breakNaturally} so fortune/silk on
 * the weapon still apply.
 */
public final class MiningRayBehavior {

    /** Tunables resolved from config. */
    public record Settings(double range, double arcDegrees, double stepDegrees, double maxHardness) {
    }

    private final Settings settings;

    public MiningRayBehavior(Settings settings) {
        this.settings = settings;
    }

    public void mine(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector base = eye.getDirection();
        ItemStack tool = player.getInventory().getItemInMainHand();

        Set<Block> targets = new LinkedHashSet<>();
        for (double angle = -settings.arcDegrees(); angle <= settings.arcDegrees() + 1.0e-9;
                angle += settings.stepDegrees()) {
            Vector dir = base.clone().rotateAroundY(Math.toRadians(angle));
            RayTraceResult hit = world.rayTraceBlocks(eye, dir, settings.range(),
                    FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitBlock() != null) {
                targets.add(hit.getHitBlock());
            }
        }

        boolean brokeAny = false;
        for (Block block : targets) {
            Material type = block.getType();
            if (type.isAir()) {
                continue;
            }
            float hardness = type.getHardness();
            if (hardness < 0 || hardness > settings.maxHardness()) {
                continue; // unbreakable (e.g. bedrock) or too hard (e.g. obsidian)
            }
            block.breakNaturally(tool);
            brokeAny = true;
        }

        if (brokeAny) {
            world.playSound(eye, Sound.BLOCK_STONE_BREAK, 0.8f, 0.8f);
        }
        world.spawnParticle(Particle.CRIT, eye.clone().add(base.clone().multiply(2)), 12,
                0.5, 0.5, 0.5, 0.0);
    }
}
