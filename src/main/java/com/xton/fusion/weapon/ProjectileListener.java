package com.xton.fusion.weapon;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.item.FusionKeys;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.weapon.behaviors.SwingEffectBehavior;

/**
 * Bow override: when a fused bow is fired, its modifier stack is stamped onto
 * the projectile, and on impact the swing-effect burst fires at the landing
 * point. So a fused bow throws its weapon effect downrange.
 */
public final class ProjectileListener implements Listener {

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final SwingEffectBehavior swingEffect;
    private final FusionKeys keys;

    public ProjectileListener(FusedItemReader reader, ModifierRegistry registry,
                              SwingEffectBehavior swingEffect, FusionKeys keys) {
        this.reader = reader;
        this.registry = registry;
        this.swingEffect = swingEffect;
        this.keys = keys;
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null || !reader.isFused(bow)) {
            return;
        }
        List<String> ids = reader.readModifierIds(bow);
        if (registry.resolve(ids).isEmpty()) {
            return;
        }
        projectile.getPersistentDataContainer().set(keys.modifierStack,
                PersistentDataType.STRING, String.join(",", ids));
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String csv = projectile.getPersistentDataContainer().get(keys.modifierStack,
                PersistentDataType.STRING);
        if (csv == null || csv.isBlank()) {
            return;
        }
        Location loc;
        if (event.getHitBlock() != null) {
            loc = event.getHitBlock().getLocation().toCenterLocation();
        } else if (event.getHitEntity() != null) {
            loc = event.getHitEntity().getLocation();
        } else {
            loc = projectile.getLocation();
        }
        ModifierStack stack = registry.resolve(Arrays.asList(csv.split(",")));
        swingEffect.burstAt(loc, stack);
        projectile.remove();
    }
}
