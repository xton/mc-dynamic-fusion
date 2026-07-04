package com.xton.fusion.projectile;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.xton.fusion.modifier.AoeSpec;

/**
 * Applies the <b>environmental</b> AOE kinds — MINING, FIRE, ICE, DEPOSIT — to
 * the world. Split in two so the projectile can drive each independently:
 *
 * <ul>
 *   <li>{@link #applyBlocks} sweeps the blocks in a radius (break / ignite /
 *       freeze / fill), called at each block cell along the flight (occupied if
 *       PIERCE, empty if TRAIL) and at the terminus;</li>
 *   <li>{@link #applyEntity} applies the mob effect (FIRE burns, ICE freezes) to
 *       one entity, called at each entity the shot contacts and for entities in
 *       range at the terminus — deduped per shot so a mob is touched once.</li>
 * </ul>
 */
public final class EnvironmentalAoe {

    /** Tunables resolved from config. */
    public record Settings(int fireBurnTicks, int iceFreezeTicks, double maxRadius, double maxHardness) {
    }

    private final World world;
    private final Player caster;
    private final Settings settings;
    private final Set<UUID> touched = new HashSet<>();

    public EnvironmentalAoe(World world, Player caster, Settings settings) {
        this.world = world;
        this.caster = caster;
        this.settings = settings;
    }

    // ----- blocks -----

    /** Sweep the blocks in {@code aoe}'s radius around {@code where}. */
    public void applyBlocks(AoeSpec aoe, Location where) {
        double r = Math.min(aoe.radius(), settings.maxRadius());
        switch (aoe.kind()) {
            case MINING -> {
                // The mining element's power carries its break-hardness cap (raised
                // by stacking MINING / AMPLIFY), bounded by the global safety ceiling.
                double base = aoe.power() > 0 ? aoe.power() : settings.maxHardness();
                double cap = Math.min(base, settings.maxHardness());
                if (forEachBlock(where, r, block -> breakSoft(block, cap))) {
                    world.playSound(where, Sound.BLOCK_STONE_BREAK, 0.6f, 0.9f);
                }
            }
            case FIRE -> {
                if (forEachBlock(where, r, this::ignite)) {
                    world.playSound(where, Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.0f);
                }
            }
            case ICE -> {
                if (forEachBlock(where, r, this::freeze)) {
                    world.playSound(where, Sound.BLOCK_GLASS_PLACE, 0.5f, 1.4f);
                }
            }
            case DEPOSIT -> {
                Material material = aoe.material();
                if (material != null && forEachBlock(where, r, block -> fillAir(block, material))) {
                    world.playSound(where, Sound.BLOCK_ROOTED_DIRT_PLACE, 0.5f, 1.0f);
                }
            }
            default -> {
                // PUSH/DAMAGE are entity bursts, handled elsewhere.
            }
        }
    }

    private boolean breakSoft(Block block, double maxHardness) {
        Material type = block.getType();
        if (type.isAir() || !isBreakable(type, maxHardness)) {
            return false;
        }
        world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                8, 0.2, 0.2, 0.2, 0.0, type.createBlockData());
        ItemStack tool = caster != null ? caster.getInventory().getItemInMainHand() : null;
        if (tool != null) {
            block.breakNaturally(tool);
        } else {
            block.breakNaturally();
        }
        return true;
    }

    private boolean ignite(Block block) {
        Material type = block.getType();
        if (isSnow(type)) {
            block.setType(Material.AIR, true);
            return true;
        }
        if (isIce(type)) {
            block.setType(Material.WATER, true);
            return true;
        }
        if (type.isAir() && hasSupport(block)) {
            block.setType(Material.FIRE, true); // a real, spreading fire block
            return true;
        }
        return false;
    }

    private boolean freeze(Block block) {
        Material type = block.getType();
        if (type == Material.WATER) {
            block.setType(Material.ICE, true);
            return true;
        }
        if (type == Material.LAVA) {
            block.setType(Material.OBSIDIAN, true);
            return true;
        }
        if (type == Material.FIRE || type == Material.SOUL_FIRE) {
            block.setType(Material.AIR, true);
            return true;
        }
        // Dressing: lay a thin snow layer on an exposed solid surface, so the freeze
        // always reads even with no water/lava/fire around to transform.
        if (type.isAir() && canHoldSnow(block.getRelative(0, -1, 0))) {
            block.setType(Material.SNOW, true);
            return true;
        }
        return false;
    }

    /** True if a snow layer will sit on this block: a solid top that isn't already snowy/icy. */
    private boolean canHoldSnow(Block below) {
        Material b = below.getType();
        return b.isSolid() && !isSnow(b) && !isIce(b) && b != Material.SNOW;
    }

    private boolean fillAir(Block block, Material material) {
        if (block.getType().isAir()) {
            block.setType(material, true);
            return true;
        }
        return false;
    }

    // ----- entities -----

    /** Apply the mob effect for {@code aoe} (FIRE burns, ICE freezes) once per shot. */
    public void applyEntity(AoeSpec aoe, LivingEntity target) {
        if (target.equals(caster) || !touched.add(target.getUniqueId())) {
            return;
        }
        switch (aoe.kind()) {
            case FIRE -> target.setFireTicks(Math.max(target.getFireTicks(), settings.fireBurnTicks()));
            case ICE -> target.setFreezeTicks(settings.iceFreezeTicks());
            default -> {
            }
        }
    }

    // ----- helpers -----

    private interface BlockOp {
        boolean apply(Block block);
    }

    /** Run {@code op} on every block whose centre is within {@code r} of {@code where}. */
    private boolean forEachBlock(Location where, double r, BlockOp op) {
        double rSq = r * r;
        int ri = (int) Math.ceil(r);
        int cx = where.getBlockX();
        int cy = where.getBlockY();
        int cz = where.getBlockZ();
        boolean any = false;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    boolean centre = dx == 0 && dy == 0 && dz == 0;
                    if (!centre && dx * dx + dy * dy + dz * dz >= rSq) {
                        continue;
                    }
                    any |= op.apply(world.getBlockAt(cx + dx, cy + dy, cz + dz));
                }
            }
        }
        return any;
    }

    private boolean hasSupport(Block air) {
        if (!air.getRelative(0, -1, 0).getType().isAir()) {
            return true;
        }
        for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}) {
            if (air.getRelative(d[0], d[1], d[2]).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    private boolean isBreakable(Material type, double maxHardness) {
        double hardness = type.getHardness();
        return hardness >= 0 && hardness <= maxHardness; // bedrock (-1) is never breakable
    }

    private static boolean isSnow(Material type) {
        return type == Material.SNOW || type == Material.SNOW_BLOCK || type == Material.POWDER_SNOW;
    }

    private static boolean isIce(Material type) {
        return type == Material.ICE || type == Material.PACKED_ICE
                || type == Material.BLUE_ICE || type == Material.FROSTED_ICE;
    }
}
