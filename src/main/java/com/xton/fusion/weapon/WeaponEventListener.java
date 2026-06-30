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
import com.xton.fusion.modifier.impl.MiningModifier;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.weapon.behaviors.MiningRayBehavior;
import com.xton.fusion.weapon.behaviors.SwingEffectBehavior;

/** Routes melee swings of fused weapons to the swing-effect and mining behaviours. */
public final class WeaponEventListener implements Listener {

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final SwingEffectBehavior swingEffect;
    private final MiningRayBehavior miningRay;
    private final CooldownMap cooldown;

    public WeaponEventListener(FusedItemReader reader,
                               ModifierRegistry registry,
                               SwingEffectBehavior swingEffect,
                               MiningRayBehavior miningRay,
                               CooldownMap cooldown) {
        this.reader = reader;
        this.registry = registry;
        this.swingEffect = swingEffect;
        this.miningRay = miningRay;
        this.cooldown = cooldown;
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
        ModifierStack stack = registry.resolve(ids);
        if (stack.isEmpty()) {
            return; // fused, but no implemented modifiers to act on
        }
        if (!cooldown.tryUse(player.getUniqueId())) {
            return;
        }
        swingEffect.execute(player, stack);
        if (stack.contains(MiningModifier.ID)) {
            miningRay.mine(player);
        }
    }
}
