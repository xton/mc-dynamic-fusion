package com.xton.fusion.item;

import java.util.Arrays;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Reads fusion PDC data off an existing item. */
public final class FusedItemReader {

    private final FusionKeys keys;

    public FusedItemReader(FusionKeys keys) {
        this.keys = keys;
    }

    public boolean isFused(ItemStack item) {
        PersistentDataContainer pdc = pdcOf(item);
        if (pdc == null) {
            return false;
        }
        Byte flag = pdc.get(keys.isFused, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    /** Modifier IDs in stored order; empty if the item is not fused. */
    public List<String> readModifierIds(ItemStack item) {
        PersistentDataContainer pdc = pdcOf(item);
        if (pdc == null) {
            return List.of();
        }
        String csv = pdc.get(keys.modifierStack, PersistentDataType.STRING);
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Stored generation (1 = first fusion); 0 if the item is not fused. */
    public int generation(ItemStack item) {
        PersistentDataContainer pdc = pdcOf(item);
        if (pdc == null) {
            return 0;
        }
        Integer gen = pdc.get(keys.generation, PersistentDataType.INTEGER);
        return gen == null ? 0 : gen;
    }

    private PersistentDataContainer pdcOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }
}
