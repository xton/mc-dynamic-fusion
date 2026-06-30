package com.xton.fusion.machine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;

import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.fusion.FusionResult;
import com.xton.fusion.item.FusionKeys;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The Fusion Machine GUI, built on the vanilla <b>anvil</b> interface: the left
 * input is the Target (kept and upgraded), the right input is the Ingredient
 * (consumed), and the result slot shows/yields the fused weapon — exactly the
 * familiar anvil flow, so it reads at a glance.
 *
 * <p>We open a controlled anvil view, compute the result in
 * {@link #onPrepare}, and intercept taking the result in {@link #onClick} to
 * apply the XP cost and consume the inputs ourselves. The anvil returns unused
 * inputs to the player on close, so no manual item return is needed.
 */
public final class FusionMachineMenu {

    private static final int RESULT_SLOT = 2;

    private final FusionEngine engine;
    private final FusionKeys keys;
    private final int cost;

    /** Players with a fusion anvil open → the machine block that opened it. */
    private final Map<UUID, Location> openAnvils = new HashMap<>();

    public FusionMachineMenu(FusionEngine engine, FusionKeys keys, int cost) {
        this.engine = engine;
        this.keys = keys;
        this.cost = cost;
    }

    // ----- machine item -----

    public ItemStack createMachineItem() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.machine, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(plain("Fusion Machine", NamedTextColor.GOLD));
        meta.lore(List.of(plain("Place it, then right-click to fuse.", NamedTextColor.GRAY)));
        try {
            meta.setEnchantmentGlintOverride(true);
        } catch (Throwable ignored) {
            // cosmetic
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMachineItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(keys.machine, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- opening -----

    public void open(Player player, Location machine) {
        if (player.openAnvil(null, true) != null) {
            openAnvils.put(player.getUniqueId(), machine);
        }
    }

    // ----- event handling (routed by MachineListener) -----

    /** Compute the fusion result for the anvil's two inputs as a live preview. */
    public void onPrepare(PrepareAnvilEvent event) {
        HumanEntity viewer = event.getView().getPlayer();
        if (!openAnvils.containsKey(viewer.getUniqueId())) {
            return;
        }
        AnvilInventory inv = event.getInventory();
        FusionResult result = engine.fuse(inv.getItem(0), inv.getItem(1));
        event.setResult(result.success() ? result.output() : null);
        // We handle the XP cost ourselves; don't let a vanilla level cost block the result.
        if (event.getView() instanceof AnvilView anvilView) {
            anvilView.setRepairCost(0);
        }
    }

    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Location machine = openAnvils.get(player.getUniqueId());
        if (machine == null || !(event.getView().getTopInventory() instanceof AnvilInventory inv)) {
            return;
        }
        if (event.getRawSlot() != RESULT_SLOT) {
            return; // input slots behave like a normal anvil
        }
        event.setCancelled(true);

        FusionResult result = engine.fuse(inv.getItem(0), inv.getItem(1));
        if (!result.success()) {
            return;
        }
        if (cost > 0 && player.getLevel() < cost) {
            player.sendMessage(plain("Fusing costs " + cost + " XP levels.", NamedTextColor.RED));
            return;
        }
        if (cost > 0) {
            player.giveExpLevels(-cost);
        }

        deliver(player, result.output());
        consume(inv, 0); // Target is upgraded into the result
        consume(inv, 1); // Ingredient is spent
        inv.setItem(RESULT_SLOT, null);
        player.updateInventory();
        playEffects(machine);
    }

    public void onClose(InventoryCloseEvent event) {
        openAnvils.remove(event.getPlayer().getUniqueId());
    }

    // ----- internals -----

    private void deliver(Player player, ItemStack output) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(output);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        player.sendMessage(plain("✦ Fusion complete!", NamedTextColor.GREEN));
    }

    private void consume(Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item == null) {
            return;
        }
        if (item.getAmount() <= 1) {
            inv.setItem(slot, null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }
    }

    private void playEffects(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Location center = loc.clone().add(0.5, 1.0, 0.5);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 40, 0.4, 0.6, 0.4, 0.2);
        world.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
