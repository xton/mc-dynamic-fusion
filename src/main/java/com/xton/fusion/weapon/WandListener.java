package com.xton.fusion.weapon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.modifier.ProjectileSpec;
import com.xton.fusion.projectile.PotionCloud;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.util.WorldFilter;

/**
 * The Wand: a fused {@code STICK} carrying a {@code POTION} effect (loaded by
 * fusing a Lingering Potion). Swinging it — left-click, like any other fused
 * weapon — instantly plants {@link PotionCloud}'s lingering cloud at the block
 * you're looking at, rather than waiting on a projectile to fly there (any
 * other weapon casts the same cloud at wherever its own shot terminates —
 * see {@code PotionCloudEffect}). Its radius lives on the compiled spec (an
 * AoeSpec like any other burst, so EXPAND widens it); its duration defaults to
 * a long sit-and-use window but an explicit DURATION on the stack overrides it
 * outright. Swinging a Wand never breaks the block it's aimed at (see
 * {@link #onBlockDamage}). Non-wand fused sticks (no POTION) are ignored here.
 */
public final class WandListener implements Listener {

    /** How far (blocks) the wand reaches to find a block to cast on. */
    private static final int CAST_REACH = 5;

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final ProjectileLauncher launcher;
    private final PotionCloud.Settings settings;
    private final CooldownMap cooldown;
    private final WorldFilter worldFilter;

    /** Tick of each player's last right-click, to filter out the arm-swing that
     *  a right-click interaction (e.g. opening the Fusion Machine, trading with
     *  a villager) produces. */
    private final Map<UUID, Integer> lastRightClickTick = new HashMap<>();

    public WandListener(FusedItemReader reader, ModifierRegistry registry, ProjectileLauncher launcher,
                        PotionCloud.Settings settings, CooldownMap cooldown, WorldFilter worldFilter) {
        this.reader = reader;
        this.registry = registry;
        this.launcher = launcher;
        this.settings = settings;
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
     * Right-clicking an entity (a villager to trade, ...) fires its own
     * arm-swing animation too — a different event from {@link #onInteract},
     * which only sees right-clicks on a block or on air. Without this, that
     * swing slips past the filter above and gets misread as a real cast.
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        lastRightClickTick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    /** A Wand casts, it doesn't dig — cancel block-breaking outright while holding one. */
    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        if (potionType(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (!worldFilter.isAllowed(player.getWorld())) {
            return;
        }
        // Right-clicking (e.g. opening a container) also fires an arm swing — ignore that.
        Integer rightClick = lastRightClickTick.get(player.getUniqueId());
        if (rightClick != null && Bukkit.getCurrentTick() - rightClick <= 1) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.STICK || !reader.isFused(hand)) {
            return;
        }
        ModifierStack stack = registry.resolve(reader.readModifierIds(hand));
        ProjectileSpec spec = launcher.compile(stack, settings.cloudDurationTicks());
        PotionEffectType type = spec.potionType();
        if (type == null) {
            return; // a fused stick without POTION isn't a Wand
        }
        if (!cooldown.tryUse(player.getUniqueId())) {
            return;
        }
        Block target = player.getTargetBlockExact(CAST_REACH);
        if (target == null || target.getType().isAir()) {
            return; // nothing in reach to cast on
        }
        cast(target.getLocation().add(0.5, 1.0, 0.5), type, spec.potionRadius(), spec.lifetimeTicks());
    }

    private void cast(Location where, PotionEffectType type, double radius, int durationTicks) {
        PotionCloud.cast(where, type, radius, durationTicks, settings);
    }

    /** The POTION effect a fused Wand's hand item carries, or null if it isn't one. */
    private PotionEffectType potionType(ItemStack hand) {
        if (hand == null || hand.getType() != Material.STICK || !reader.isFused(hand)) {
            return null;
        }
        ModifierStack stack = registry.resolve(reader.readModifierIds(hand));
        return launcher.compile(stack).potionType();
    }
}
