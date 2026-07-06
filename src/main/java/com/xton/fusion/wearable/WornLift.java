package com.xton.fusion.wearable;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.impl.LiftModifier;

/** Shared check: is the player's chestplate slot (a chestplate or elytra) fused with LIFT? */
final class WornLift {

    private WornLift() {
    }

    static boolean isWorn(FusedItemReader reader, Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        return chest != null && reader.isFused(chest) && reader.readModifierIds(chest).contains(LiftModifier.ID);
    }
}
