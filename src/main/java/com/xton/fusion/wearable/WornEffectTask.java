package com.xton.fusion.wearable;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.impl.GlowModifier;

/**
 * Applies the passive effects of <b>worn</b> fused armor, ticked on a repeat. For
 * now that's GLOW — a piece of fused armor with GLOW keeps night vision on its
 * wearer, so you always carry a light. The effect is refreshed with plenty of
 * headroom so it never flickers while the armor stays on; it simply lapses a few
 * seconds after you take the armor off.
 */
public final class WornEffectTask implements Runnable {

    /** Refreshed duration (ticks) — comfortably longer than the task period so night vision doesn't flicker. */
    private static final int GLOW_DURATION_TICKS = 400;

    private final FusedItemReader reader;

    public WornEffectTask(FusedItemReader reader) {
        this.reader = reader;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (wearsModifier(player, GlowModifier.ID)) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NIGHT_VISION, GLOW_DURATION_TICKS, 0, true, false, false));
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
