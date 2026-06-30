package com.xton.fusion.weapon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
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

    /** Tick of each player's last right-click, to filter out the arm-swing that
     *  a right-click interaction (e.g. opening the Fusion Machine) produces. */
    private final Map<UUID, Integer> lastRightClickTick = new HashMap<>();

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
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            lastRightClickTick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        // Right-clicking an interactive block (like the machine anvil) also fires
        // an arm swing — ignore that so opening the GUI doesn't trigger the weapon.
        Integer rightClick = lastRightClickTick.get(player.getUniqueId());
        if (rightClick != null && Bukkit.getCurrentTick() - rightClick <= 1) {
            return;
        }
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
