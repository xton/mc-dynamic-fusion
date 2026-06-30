package com.xton.fusion.fusion;

import org.bukkit.inventory.ItemStack;

/** Outcome of a fusion attempt: either a finished item or a failure message. */
public record FusionResult(boolean success, ItemStack output, String message) {

    public static FusionResult ok(ItemStack output) {
        return new FusionResult(true, output, null);
    }

    public static FusionResult fail(String message) {
        return new FusionResult(false, null, message);
    }
}
