package com.xton.fusion.weapon;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.projectile.ProjectileLauncher;

/**
 * Bow override: a fused bow throws its weapon effect downrange. On release we
 * cancel the vanilla arrow and launch the bow's modifier stack as
 * {@link com.xton.fusion.projectile.FusionProjectile}s instead, scaling their
 * speed by draw force — so the bow becomes a wand firing the same projectile
 * model as a melee swing (multishot bows fan into a volley, etc.).
 */
public final class ProjectileListener implements Listener {

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final ProjectileLauncher launcher;

    public ProjectileListener(FusedItemReader reader, ModifierRegistry registry,
                              ProjectileLauncher launcher) {
        this.reader = reader;
        this.registry = registry;
        this.launcher = launcher;
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null || !reader.isFused(bow)) {
            return;
        }
        List<String> ids = reader.readModifierIds(bow);
        ModifierStack stack = registry.resolve(ids);
        if (stack.isEmpty()) {
            return; // fused bow, but no implemented modifiers — leave it vanilla
        }
        // Replace the vanilla arrow with our own projectile(s). The bow launches
        // a ranged, arcing shot whose speed scales with draw force.
        event.setCancelled(true);
        launcher.launchBow(player, stack, event.getForce());
    }
}
