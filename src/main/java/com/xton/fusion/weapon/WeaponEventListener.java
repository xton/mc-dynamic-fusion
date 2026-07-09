package com.xton.fusion.weapon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.modifier.impl.PotionModifier;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.util.WorldFilter;

/**
 * Routes melee swings of fused weapons into launched projectiles. A swing fires
 * the weapon's stack as one or more {@link com.xton.fusion.projectile.FusionProjectile}
 * bolts — a short, fast shot that triggers where it lands (Noita-style), so the
 * melee and bow paths share the same projectile model.
 */
public final class WeaponEventListener implements Listener {

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final ProjectileLauncher launcher;
    private final CooldownMap cooldown;
    private final WorldFilter worldFilter;

    /** Tick of each player's last right-click, to filter out the arm-swing that
     *  a right-click interaction (e.g. opening the Fusion Machine, trading with
     *  a villager) produces. */
    private final Map<UUID, Integer> lastRightClickTick = new HashMap<>();

    public WeaponEventListener(FusedItemReader reader,
                               ModifierRegistry registry,
                               ProjectileLauncher launcher,
                               CooldownMap cooldown,
                               WorldFilter worldFilter) {
        this.reader = reader;
        this.registry = registry;
        this.launcher = launcher;
        this.cooldown = cooldown;
        this.worldFilter = worldFilter;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            lastRightClickTick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    /**
     * Right-clicking an entity (a villager to trade, a horse to mount, ...)
     * fires its own arm-swing animation too — a different event from
     * {@link #onInteract}, which only sees right-clicks on a block or on air.
     * Without this, that swing slips past the filter above and gets misread as
     * a real attack.
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        lastRightClickTick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (!worldFilter.isAllowed(player.getWorld())) {
            return; // outside the allowed worlds — swing stays plain vanilla
        }
        // Right-clicking an interactive block (like the machine) also fires an
        // arm swing — ignore that so opening the GUI doesn't trigger the weapon.
        Integer rightClick = lastRightClickTick.get(player.getUniqueId());
        if (rightClick != null && Bukkit.getCurrentTick() - rightClick <= 1) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!reader.isFused(hand)) {
            return;
        }
        // A fused bow/crossbow left-clicks as a plain vanilla melee — its modifiers
        // fire only on the arrow it shoots (the ProjectileListener path), not the swing.
        if (isShootable(hand.getType())) {
            return;
        }
        List<String> ids = reader.readModifierIds(hand);
        ModifierStack stack = registry.resolve(ids);
        if (stack.isEmpty()) {
            return; // fused, but no implemented modifiers to act on
        }
        // A STICK carrying POTION is the Wand — WandListener already casts its
        // cloud instantly at the crosshair on this same swing; don't also fire
        // a melee bolt that would cast a second one at its (near-instant) terminus.
        if (hand.getType() == Material.STICK && isWandCast(stack)) {
            return;
        }
        if (!cooldown.tryUse(player.getUniqueId())) {
            return;
        }
        launcher.launchMelee(player, stack);
    }

    /** Ranged weapons whose fusion fires on the projectile they shoot, not on a swing. */
    private static boolean isShootable(Material type) {
        return type == Material.BOW || type == Material.CROSSBOW;
    }

    private static boolean isWandCast(ModifierStack stack) {
        return stack.ids().stream()
                .anyMatch(id -> id.equals(PotionModifier.ID) || id.startsWith(PotionModifier.ID + ":"));
    }
}
