package com.xton.fusion.wearable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.modifier.ProjectileSpec;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.util.WorldFilter;

/**
 * Worn armor auras: fusing <em>any</em> emitter onto armor (FIRE/ICE, but
 * equally PUSH/DAMAGE/DEPOSIT/... — armor is just another possible source of
 * a shot) periodically fires a stationary, zero-duration
 * {@link ProjectileLauncher#launchAnchored anchor} rooted at the wearer's own
 * location, using the exact same compile/payload/environmental-sweep pipeline
 * a real weapon's shot does. All four armor pieces' fused ids are combined
 * into one stack first (the same RPN nearest-previous binding a weapon's own
 * id list gets), so e.g. FIRE on a helmet and EXPAND on boots compose the way
 * you'd expect.
 *
 * <p>A pulse fires whenever <em>either</em> {@code worn.aura-period-ticks}
 * has elapsed since the last one <em>or</em> the wearer has walked
 * {@code worn.aura-distance-blocks} since then — standing still still gets a
 * steady heartbeat, and moving quickly leaves a denser trail of pulses,
 * rather than the aura only ever ticking on a clock.
 *
 * <p>The wearer is always excluded from their own burst/environmental effects
 * (see {@code EnvironmentalAoe#applyEntity}/{@code AoeBurst#asTarget}, which
 * never touch their own caster) — but a real hazard an aura leaves behind
 * (FIRE's actual vanilla fire blocks underfoot; DEPOSIT:LAVA's real lava) can
 * still burn the wearer through ordinary vanilla mechanics no AOE bookkeeping
 * covers. So each tick, the wearer's compiled stack is inspected for any AOE
 * kind with a known real-world hazard and topped up on the matching immunity
 * (Fire Resistance for fire/lava), the same "refresh with headroom" treatment
 * {@link WornEffectTask} gives GLOW. ICE's aura never creates a real hazard
 * block (a plain snow layer, not powder snow), so it needs no equivalent.
 */
public final class WornAuraTask implements Runnable, Listener {

    /** Refreshed duration (ticks) — comfortably longer than the task period so it doesn't flicker/lapse mid-tick. */
    private static final int IMMUNITY_DURATION_TICKS = 100;

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final ProjectileLauncher launcher;
    private final WorldFilter worldFilter;
    private final int periodTicks;
    private final double distanceBlocks;

    private final Map<UUID, Integer> lastPulseTick = new HashMap<>();
    private final Map<UUID, Location> lastPulseLocation = new HashMap<>();

    public WornAuraTask(FusedItemReader reader, ModifierRegistry registry, ProjectileLauncher launcher,
                        WorldFilter worldFilter, int periodTicks, double distanceBlocks) {
        this.reader = reader;
        this.registry = registry;
        this.launcher = launcher;
        this.worldFilter = worldFilter;
        this.periodTicks = periodTicks;
        this.distanceBlocks = distanceBlocks;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!worldFilter.isAllowed(player.getWorld())) {
                continue;
            }
            ModifierStack stack = wornStack(player);
            if (stack.isEmpty()) {
                continue;
            }
            ProjectileSpec spec = launcher.compile(stack);
            if (spec.payload().isEmpty()) {
                continue; // fused, but nothing that emits anything
            }

            if (due(id, player.getLocation())) {
                launcher.launchAnchored(player, stack);
                lastPulseTick.put(id, Bukkit.getCurrentTick());
                lastPulseLocation.put(id, player.getLocation());
            }
            refreshImmunity(player, spec);
        }
    }

    /** Due if either the timer or the distance-travelled threshold has been crossed since the last pulse. */
    private boolean due(UUID id, Location now) {
        Integer lastTick = lastPulseTick.get(id);
        if (lastTick == null || Bukkit.getCurrentTick() - lastTick >= periodTicks) {
            return true;
        }
        Location last = lastPulseLocation.get(id);
        if (last == null || !Objects.equals(last.getWorld(), now.getWorld())) {
            return true; // never pulsed yet, or switched worlds since — treat as due
        }
        return last.distance(now) >= distanceBlocks;
    }

    /** Every fused armor piece's modifier ids, combined in slot order into one stack. */
    private ModifierStack wornStack(Player player) {
        List<String> ids = new ArrayList<>();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && reader.isFused(piece)) {
                ids.addAll(reader.readModifierIds(piece));
            }
        }
        return registry.resolve(ids);
    }

    /** Top up whatever real-world-hazard immunity the wearer's compiled aura currently calls for. */
    private void refreshImmunity(Player player, ProjectileSpec spec) {
        for (AoeSpec aoe : spec.payload()) {
            PotionEffectType immunity = immunityFor(aoe);
            if (immunity != null) {
                player.addPotionEffect(new PotionEffect(immunity, IMMUNITY_DURATION_TICKS, 0, true, false, false));
            }
        }
    }

    /** The real-world hazard {@code aoe} would otherwise expose its own caster to, or null if none. */
    private static PotionEffectType immunityFor(AoeSpec aoe) {
        if (aoe.kind() == AoeKind.FIRE) {
            return PotionEffectType.FIRE_RESISTANCE; // real, spreading fire blocks underfoot
        }
        if (aoe.kind() == AoeKind.DEPOSIT && aoe.material() == Material.LAVA) {
            return PotionEffectType.FIRE_RESISTANCE; // real lava — Fire Resistance covers lava damage too
        }
        return null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastPulseTick.remove(id);
        lastPulseLocation.remove(id);
    }
}
