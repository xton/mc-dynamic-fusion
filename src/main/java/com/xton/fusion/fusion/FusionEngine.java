package com.xton.fusion.fusion;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.xton.fusion.item.FusedItemFactory;
import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.item.LatentRegistry;
import com.xton.fusion.item.Lineage;

/**
 * Core merge logic. Fusion is asymmetric: the Target defines the output base
 * type and carries its existing modifiers forward; the Ingredient is consumed,
 * contributing its latent modifiers.
 *
 * <p>Duplicates are kept (no dedupe). The modifier cap is a technical guard,
 * not a balance lever — excess is dropped from the tail.
 */
public final class FusionEngine {

    private final LatentRegistry latent;
    private final FusedItemReader reader;
    private final FusedItemFactory factory;
    private final int maxModifiers;

    public FusionEngine(LatentRegistry latent,
                        FusedItemReader reader,
                        FusedItemFactory factory,
                        int maxModifiers) {
        this.latent = latent;
        this.reader = reader;
        this.factory = factory;
        this.maxModifiers = maxModifiers;
    }

    public FusionResult fuse(ItemStack target, ItemStack ingredient) {
        if (isEmpty(target)) {
            return FusionResult.fail("No weapon to enhance.");
        }
        if (isEmpty(ingredient)) {
            return FusionResult.fail("No ingredient to fuse.");
        }

        // The ingredient contributes its base-material latent modifiers AND, if
        // it is itself a fused item, its whole fused stack.
        List<String> contributed = new ArrayList<>(latent.get(ingredient.getType()));
        if (reader.isFused(ingredient)) {
            contributed.addAll(reader.readModifierIds(ingredient));
        }
        if (contributed.isEmpty()) {
            return FusionResult.fail(pretty(ingredient.getType()) + " has no magic to give.");
        }

        List<String> merged = new ArrayList<>();
        if (reader.isFused(target)) {
            merged.addAll(reader.readModifierIds(target));
        }
        merged.addAll(contributed);
        if (merged.size() > maxModifiers) {
            merged = new ArrayList<>(merged.subList(0, maxModifiers));
        }

        // Accumulate the provenance: the target's existing lineage (base +
        // prior ingredients) plus this ingredient, so the "Fused from" line grows
        // instead of only ever showing the last pair.
        List<String> lineage = new ArrayList<>();
        if (reader.isFused(target)) {
            lineage.addAll(Lineage.split(reader.fusedFrom(target)));
        }
        if (lineage.isEmpty()) {
            lineage.add(pretty(target.getType()));
        }
        lineage.add(pretty(ingredient.getType()));

        ItemStack output = factory.create(target.getType(), merged, Lineage.join(lineage));
        return FusionResult.ok(output);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    /** {@code NETHER_STAR} -> {@code "Nether Star"}. */
    static String pretty(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
