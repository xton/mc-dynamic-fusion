package com.xton.fusion.weapon;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.modifier.impl.NovaModifier;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.weapon.behaviors.NovaBehavior;

/** Routes melee swings to weapon behaviours. */
public final class WeaponEventListener implements Listener {

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final NovaBehavior nova;
    private final CooldownMap novaCooldown;

    public WeaponEventListener(FusedItemReader reader,
                               ModifierRegistry registry,
                               NovaBehavior nova,
                               CooldownMap novaCooldown) {
        this.reader = reader;
        this.registry = registry;
        this.nova = nova;
        this.novaCooldown = novaCooldown;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!reader.isFused(hand)) {
            return;
        }
        List<String> ids = reader.readModifierIds(hand);
        if (!ids.contains(NovaModifier.ID)) {
            return;
        }
        if (!novaCooldown.tryUse(player.getUniqueId())) {
            return;
        }
        ModifierStack stack = registry.resolve(ids);
        nova.execute(player, stack);
    }
}
