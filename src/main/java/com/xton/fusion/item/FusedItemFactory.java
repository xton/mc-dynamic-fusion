package com.xton.fusion.item;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Creates a fused output {@link ItemStack} from a resolved modifier list. */
public final class FusedItemFactory {

    private final FusionKeys keys;
    private final LoreGenerator lore;

    public FusedItemFactory(FusionKeys keys, LoreGenerator lore) {
        this.keys = keys;
        this.lore = lore;
    }

    public ItemStack create(Material base, List<String> modifierIds, int generation, String fusedFrom) {
        ItemStack out = new ItemStack(base);
        ItemMeta meta = out.getItemMeta();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.isFused, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keys.modifierStack, PersistentDataType.STRING, String.join(",", modifierIds));
        pdc.set(keys.generation, PersistentDataType.INTEGER, generation);
        pdc.set(keys.fusedFrom, PersistentDataType.STRING, fusedFrom);

        meta.displayName(Component.text("Fusion Weapon", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.generate(modifierIds, generation, fusedFrom));

        // Cosmetic glint. Optional under test harnesses that don't implement it.
        try {
            meta.setEnchantmentGlintOverride(true);
        } catch (Throwable ignored) {
            // MockBukkit may not support the glint override; it isn't load-bearing.
        }

        out.setItemMeta(meta);
        return out;
    }
}
