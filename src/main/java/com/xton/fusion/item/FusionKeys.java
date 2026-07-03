package com.xton.fusion.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Namespaced PDC keys used to mark and describe fused items. */
public final class FusionKeys {

    public final NamespacedKey isFused;
    public final NamespacedKey modifierStack;
    public final NamespacedKey fusedFrom;
    public final NamespacedKey machine;

    public FusionKeys(Plugin plugin) {
        this.isFused = new NamespacedKey(plugin, "is_fused");
        this.modifierStack = new NamespacedKey(plugin, "modifier_stack");
        this.fusedFrom = new NamespacedKey(plugin, "fused_from");
        this.machine = new NamespacedKey(plugin, "machine");
    }
}
