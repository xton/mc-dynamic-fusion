package com.xton.fusion.weapon;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.machine.FusionMachineMenu;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.util.WorldFilter;

/**
 * The Golden Brush: right-clicking (brushing) with a fused {@code BRUSH} that
 * carries TREASURE rolls the loot table. The loot <em>level</em> is the brush's
 * TREASURE count (how much gold was fused): it raises the proc chance and unlocks
 * rarer finds. Every successful stroke scours the block — win or not — so a spot
 * can't be farmed forever: solid ground scours down to {@link Material#COARSE_DIRT}
 * (which itself doesn't brush — nothing left to find), while a non-solid plant or
 * decoration (grass, ferns, flowers, ...) just breaks instead, since it was never
 * "ground" to begin with. A per-player cooldown keeps it from firehosing.
 * Non-brush fused weapons are ignored here (they swing/shoot as normal), and a
 * placed Fusion Machine is never touched.
 */
public final class GoldenBrushListener implements Listener {

    private final FusedItemReader reader;
    private final ModifierRegistry registry;
    private final ProjectileLauncher launcher;
    private final GoldenBrush brush;
    private final CooldownMap cooldown;
    private final WorldFilter worldFilter;
    private final FusionMachineMenu menu;
    private final Random rng = new Random();

    public GoldenBrushListener(FusedItemReader reader, ModifierRegistry registry,
                               ProjectileLauncher launcher, GoldenBrush brush, CooldownMap cooldown,
                               WorldFilter worldFilter, FusionMachineMenu menu) {
        this.reader = reader;
        this.registry = registry;
        this.launcher = launcher;
        this.brush = brush;
        this.cooldown = cooldown;
        this.worldFilter = worldFilter;
        this.menu = menu;
    }

    @EventHandler
    public void onBrush(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main-hand only — don't double-fire on the off-hand pass
        }
        Player player = event.getPlayer();
        if (!worldFilter.isAllowed(player.getWorld())) {
            return; // outside the allowed worlds — brushing finds nothing
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.BRUSH || !reader.isFused(hand)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && (clicked.getType() == Material.COARSE_DIRT || menu.isMachineBlock(clicked))) {
            return; // already scoured (or a placed Fusion Machine) — nothing left to brush
        }
        ModifierStack stack = registry.resolve(reader.readModifierIds(hand));
        int level = launcher.compile(stack).treasure();
        if (level <= 0) {
            return; // a fused brush without TREASURE isn't a Golden Brush
        }
        if (!cooldown.tryUse(player.getUniqueId())) {
            return;
        }

        Location where = clicked != null
                ? clicked.getLocation().add(0.5, 1.0, 0.5)
                : player.getEyeLocation().add(player.getEyeLocation().getDirection());
        where.getWorld().playSound(where, Sound.ITEM_BRUSH_BRUSHING_GENERIC, 0.7f, 1.0f);

        // Every stroke scours the block, win or not — so a spot can't be
        // brushed forever. Solid ground scours down to coarse dirt; a
        // non-solid plant/decoration (grass, ferns, flowers, ...) isn't
        // "ground" to scour — it just breaks, like a quick brush would.
        if (clicked != null) {
            if (clicked.getType().isSolid()) {
                clicked.setType(Material.COARSE_DIRT);
            } else {
                clicked.breakNaturally();
            }
        }

        if (rng.nextDouble() >= brush.procChance(level)) {
            return; // no find this stroke
        }
        Material loot = brush.roll(level, rng);
        if (loot == null) {
            return;
        }
        where.getWorld().dropItem(where, new ItemStack(loot));
        where.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, where, 6, 0.3, 0.3, 0.3, 0.0);
        where.getWorld().playSound(where, Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.4f);
    }
}
