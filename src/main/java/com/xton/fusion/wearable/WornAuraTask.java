package com.xton.fusion.wearable;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.modifier.ProjectileSpec;
import com.xton.fusion.projectile.EnvironmentalAoe;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.util.WorldFilter;

/**
 * Worn FIRE/ICE armor auras: a piece of fused armor carrying FIRE or ICE
 * ignites/freezes the blocks and creatures in a radius around the
 * <em>wearer</em>, ticked on a repeat — the same environmental sweep a
 * FIRE/ICE projectile does at its terminus (radius widened by EXPAND, same
 * as any other emitter), just centred on a person instead of a landing spot.
 *
 * <p>The wearer is always excluded from their own aura's mob effects (see
 * {@link EnvironmentalAoe#applyEntity}, which never touches its caster) — but
 * FIRE also drops real, vanilla fire blocks underfoot, which would otherwise
 * burn the wearer through ordinary vanilla mechanics no AOE bookkeeping
 * covers. So a FIRE-aura wearer is kept topped up on Fire Resistance the
 * whole time the armor's on, the same "refresh with headroom, let it lapse a
 * few seconds after taking the armor off" treatment {@link WornEffectTask}
 * gives GLOW. ICE's aura never creates a real hazard block (a plain snow
 * layer, not powder snow), so it needs no equivalent.
 */
public final class WornAuraTask implements Runnable {

    /** Refreshed duration (ticks) — comfortably longer than the task period so it doesn't flicker/lapse mid-tick. */
    private static final int FIRE_RESISTANCE_DURATION_TICKS = 100;

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final ProjectileLauncher launcher;
    private final EnvironmentalAoe.Settings envSettings;
    private final WorldFilter worldFilter;

    public WornAuraTask(FusedItemReader reader, ModifierRegistry registry, ProjectileLauncher launcher,
                        EnvironmentalAoe.Settings envSettings, WorldFilter worldFilter) {
        this.reader = reader;
        this.registry = registry;
        this.launcher = launcher;
        this.envSettings = envSettings;
        this.worldFilter = worldFilter;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!worldFilter.isAllowed(player.getWorld())) {
                continue;
            }
            AoeSpec fire = wornAoe(player, AoeKind.FIRE);
            AoeSpec ice = wornAoe(player, AoeKind.ICE);
            if (fire == null && ice == null) {
                continue;
            }
            EnvironmentalAoe env = new EnvironmentalAoe(launcher.plugin(), player.getWorld(), player, envSettings);
            if (fire != null) {
                pulse(env, player, fire);
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.FIRE_RESISTANCE, FIRE_RESISTANCE_DURATION_TICKS, 0, true, false, false));
            }
            if (ice != null) {
                pulse(env, player, ice);
            }
        }
    }

    private void pulse(EnvironmentalAoe env, Player player, AoeSpec aura) {
        env.applyBlocks(aura, player.getLocation());
        double r = Math.min(aura.radius(), envSettings.maxRadius());
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), r, r, r)) {
            if (entity instanceof LivingEntity living) {
                env.applyEntity(aura, living);
            }
        }
    }

    /** The {@code kind} AoeSpec (radius already EXPAND-scaled) from the first worn piece that carries it, or null. */
    private AoeSpec wornAoe(Player player, AoeKind kind) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || !reader.isFused(piece)) {
                continue;
            }
            ModifierStack stack = registry.resolve(reader.readModifierIds(piece));
            ProjectileSpec spec = launcher.compile(stack);
            for (AoeSpec aoe : spec.payload()) {
                if (aoe.kind() == kind) {
                    return aoe;
                }
            }
        }
        return null;
    }
}
