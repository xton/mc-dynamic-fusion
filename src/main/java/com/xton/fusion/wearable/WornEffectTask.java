package com.xton.fusion.wearable;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.impl.GlowModifier;
import com.xton.fusion.util.WorldFilter;

/**
 * Applies the passive effects of <b>worn</b> fused armor, ticked on a repeat. For
 * now that's GLOW — a piece of fused armor with GLOW keeps a strong Glowing
 * effect on its wearer (the vanilla see-through-walls outline), not night
 * vision — it makes *you* glow, not the world around you. The effect is
 * refreshed with plenty of headroom so it never flickers while the armor stays
 * on; it simply lapses a few seconds after you take the armor off.
 */
public final class WornEffectTask implements Runnable {

    /** Refreshed duration (ticks) — comfortably longer than the task period so the glow doesn't flicker. */
    private static final int GLOW_DURATION_TICKS = 400;
    /** Amplifier for the glow — Glowing has no visual level beyond 0, but a high tier still reads as "powerful". */
    private static final int GLOW_AMPLIFIER = 4;

    private final FusedItemReader reader;
    private final WorldFilter worldFilter;

    public WornEffectTask(FusedItemReader reader, WorldFilter worldFilter) {
        this.reader = reader;
        this.worldFilter = worldFilter;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (worldFilter.isAllowed(player.getWorld()) && wearsModifier(player, GlowModifier.ID)) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING, GLOW_DURATION_TICKS, GLOW_AMPLIFIER, true, false, false));
            }
        }
    }

    /** True if any piece of the player's fused armor carries {@code modifierId}. */
    private boolean wearsModifier(Player player, String modifierId) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && reader.isFused(piece) && reader.readModifierIds(piece).contains(modifierId)) {
                return true;
            }
        }
        return false;
    }
}
