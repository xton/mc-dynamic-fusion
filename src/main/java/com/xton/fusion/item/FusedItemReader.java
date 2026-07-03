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

    /** The stored "fused from" lineage line, or empty if absent/unfused. */
    public String fusedFrom(ItemStack item) {
        PersistentDataContainer pdc = pdcOf(item);
        if (pdc == null) {
            return "";
        }
        String from = pdc.get(keys.fusedFrom, PersistentDataType.STRING);
        return from == null ? "" : from;
    }

    private PersistentDataContainer pdcOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }
}
