package com.xton.fusion.weapon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.util.WorldFilter;

/**
 * The Wand: a fused {@code STICK} carrying a {@code POTION} effect (loaded by
 * fusing a Lingering Potion). Right-clicking a block plants a small, vanilla
 * {@link AreaEffectCloud} there — the same entity a thrown lingering potion
 * leaves behind — loaded with the wand's effect, so the block "exudes" it:
 * anything that lingers nearby gets dosed on a timer, and the cloud's
 * particles/color come straight from the effect's own color, no extra art
 * needed. Non-wand fused sticks (no POTION) are ignored here.
 */
public final class WandListener implements Listener {

    /** Tunables resolved from config. */
    public record Settings(double radius, int cloudDurationTicks, int effectDurationTicks, int amplifier) {
    }

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final ProjectileLauncher launcher;
    private final Settings settings;
    private final CooldownMap cooldown;
    private final WorldFilter worldFilter;

    public WandListener(FusedItemReader reader, ModifierRegistry registry, ProjectileLauncher launcher,
                        Settings settings, CooldownMap cooldown, WorldFilter worldFilter) {
        this.reader = reader;
        this.registry = registry;
        this.launcher = launcher;
        this.settings = settings;
        this.cooldown = cooldown;
        this.worldFilter = worldFilter;
    }

    @EventHandler
    public void onCast(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return; // needs a block to plant the cloud on
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main-hand only — don't double-fire on the off-hand pass
        }
        Player player = event.getPlayer();
        if (!worldFilter.isAllowed(player.getWorld())) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.STICK || !reader.isFused(hand)) {
            return;
        }
        ModifierStack stack = registry.resolve(reader.readModifierIds(hand));
        PotionEffectType type = launcher.compile(stack).potionType();
        if (type == null) {
            return; // a fused stick without POTION isn't a Wand
        }
        if (!cooldown.tryUse(player.getUniqueId()) || event.getClickedBlock() == null) {
            return;
        }

        Location where = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
        AreaEffectCloud cloud = where.getWorld().spawn(where, AreaEffectCloud.class);
        cloud.setRadius((float) settings.radius());
        cloud.setDuration(settings.cloudDurationTicks());
        cloud.setParticle(Particle.ENTITY_EFFECT);
        cloud.setColor(type.getColor());
        cloud.addCustomEffect(new PotionEffect(type, settings.effectDurationTicks(), settings.amplifier()), true);
        where.getWorld().playSound(where, Sound.ENTITY_SPLASH_POTION_BREAK, 0.7f, 1.0f);
    }
}
