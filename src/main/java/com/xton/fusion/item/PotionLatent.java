package com.xton.fusion.item;

import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/**
 * The one dynamic exception to {@link LatentRegistry}'s static Material→IDs
 * map: a {@code LINGERING_POTION} carries a different latent depending on
 * <em>which</em> potion it is (Poison vs. Harming vs. ...), so its
 * {@code POTION:<effect>} modifier has to be read off the ingredient's own
 * {@link PotionMeta} at fuse time rather than looked up by material alone.
 * Base potions with no effect (Water, Awkward, Mundane, Thick) contribute
 * nothing; a type with more than one effect (Turtle Master) only carries its
 * first — a Wand casts one effect, not a bundle.
 */
public final class PotionLatent {

    private PotionLatent() {
    }

    /** The {@code POTION:<effect>} modifier a lingering-potion ingredient contributes, or empty. */
    public static List<String> extra(ItemStack ingredient) {
        if (ingredient == null || ingredient.getType() != Material.LINGERING_POTION) {
            return List.of();
        }
        if (!(ingredient.getItemMeta() instanceof PotionMeta meta) || !meta.hasBasePotionType()) {
            return List.of();
        }
        PotionType base = meta.getBasePotionType();
        List<PotionEffect> effects = base == null ? List.of() : base.getPotionEffects();
        if (effects.isEmpty()) {
            return List.of(); // Water/Awkward/Mundane/Thick — no effect to carry
        }
        return List.of("POTION:" + effects.get(0).getType().getKey().getKey().toUpperCase(Locale.ROOT));
    }
}
