package com.xton.fusion.machine;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.fusion.FusionResult;
import com.xton.fusion.item.FusionKeys;
import com.xton.fusion.util.Scheduler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The anvil-style Fusion Machine GUI: a Target slot (kept and upgraded), an
 * Ingredient slot (consumed), a live Output preview, and a Confirm button.
 *
 * <p>Item handling is defensive: only the Target/Ingredient slots accept items,
 * shift/number/double clicks are blocked to avoid routing into locked slots,
 * and items are returned to the player on close so nothing is lost.
 */
public final class FusionMachineMenu {

    private final FusionEngine engine;
    private final Scheduler scheduler;
    private final FusionKeys keys;

    public FusionMachineMenu(FusionEngine engine, Scheduler scheduler, FusionKeys keys) {
        this.engine = engine;
        this.scheduler = scheduler;
        this.keys = keys;
    }

    // ----- machine item -----

    public ItemStack createMachineItem() {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
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
        FusionMenuHolder holder = new FusionMenuHolder(machine);
        Inventory inv = Bukkit.createInventory(holder, FusionMenuHolder.SIZE,
                plain("Fusion Machine", NamedTextColor.GOLD));
        holder.setInventory(inv);

        ItemStack pane = pane();
        for (int slot : new int[] {1, 3, 5, 6, 7}) {
            inv.setItem(slot, pane);
        }
        inv.setItem(FusionMenuHolder.CONFIRM, confirmItem());
        player.openInventory(inv);
    }

    // ----- event handling (called by MachineListener) -----

    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (!(view.getTopInventory().getHolder() instanceof FusionMenuHolder holder)) {
            return;
        }
        // Block bulk/transfer interactions that could move items into locked slots.
        ClickType click = event.getClick();
        if (event.isShiftClick() || click == ClickType.NUMBER_KEY
                || click == ClickType.SWAP_OFFHAND || click == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        int topSize = view.getTopInventory().getSize();
        if (raw < 0 || raw >= topSize) {
            return; // clicks in the player's own inventory are fine
        }
        if (raw == FusionMenuHolder.TARGET || raw == FusionMenuHolder.INGREDIENT) {
            // Allow normal placement/removal; refresh the preview after it applies.
            scheduler.runLater(() -> updatePreview(view.getTopInventory()), 1);
        } else if (raw == FusionMenuHolder.CONFIRM) {
            event.setCancelled(true);
            doFuse((Player) event.getWhoClicked(), view.getTopInventory(), holder.getMachineLocation());
        } else {
            event.setCancelled(true);
        }
    }

    public void onClose(InventoryCloseEvent event) {
        InventoryView view = event.getView();
        if (!(view.getTopInventory().getHolder() instanceof FusionMenuHolder)) {
            return;
        }
        Inventory inv = view.getTopInventory();
        Player player = (Player) event.getPlayer();
        returnItem(player, inv.getItem(FusionMenuHolder.TARGET));
        returnItem(player, inv.getItem(FusionMenuHolder.INGREDIENT));
        inv.setItem(FusionMenuHolder.TARGET, null);
        inv.setItem(FusionMenuHolder.INGREDIENT, null);
    }

    // ----- internals -----

    private void updatePreview(Inventory inv) {
        ItemStack target = inv.getItem(FusionMenuHolder.TARGET);
        ItemStack ingredient = inv.getItem(FusionMenuHolder.INGREDIENT);
        if (isEmpty(target) && isEmpty(ingredient)) {
            inv.setItem(FusionMenuHolder.OUTPUT, null);
            return;
        }
        FusionResult result = engine.fuse(target, ingredient);
        inv.setItem(FusionMenuHolder.OUTPUT, result.success() ? result.output() : hint(result.message()));
    }

    private void doFuse(Player player, Inventory inv, Location machine) {
        ItemStack target = inv.getItem(FusionMenuHolder.TARGET);
        ItemStack ingredient = inv.getItem(FusionMenuHolder.INGREDIENT);
        FusionResult result = engine.fuse(target, ingredient);
        if (!result.success()) {
            player.sendMessage(plain(result.message(), NamedTextColor.RED));
            return;
        }
        ItemStack remaining = ingredient.clone();
        remaining.setAmount(ingredient.getAmount() - 1);
        inv.setItem(FusionMenuHolder.INGREDIENT, remaining.getAmount() <= 0 ? null : remaining);
        inv.setItem(FusionMenuHolder.TARGET, result.output());
        updatePreview(inv);

        player.sendMessage(plain("✦ Fusion complete!", NamedTextColor.GREEN));
        playEffects(machine != null ? machine : player.getLocation());
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

    private void returnItem(Player player, ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    private ItemStack hint(String message) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(message, NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack confirmItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Fuse!", NamedTextColor.GREEN));
        meta.lore(List.of(plain("Click to fuse Target + Ingredient.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
