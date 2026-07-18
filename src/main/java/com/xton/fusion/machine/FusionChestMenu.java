package com.xton.fusion.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.fusion.FusionResult;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * A chest-based Fusion GUI for Bedrock players (see {@link BedrockPlayers}),
 * mirroring the anvil machine's Target/Ingredient/Result flow in a plain
 * single-row chest instead — one of Geyser's most reliably translated
 * container types, unlike the anvil.
 *
 * <p>Unlike the anvil, a plain chest has no {@code PrepareAnvilEvent} to hook
 * for a live preview, and Bukkit doesn't return its contents to the player on
 * close (that's a vanilla-anvil-specific behavior) — both are handled here
 * explicitly. There's also no rename field; Bedrock players can rename the
 * taken item with a name tag same as anyone.
 */
public final class FusionChestMenu {

    private static final int TARGET_SLOT = 0;
    private static final int INGREDIENT_SLOT = 1;
    private static final int ARROW_SLOT = 2;
    private static final int RESULT_SLOT = 3;
    private static final int SIZE = 9;

    private final Plugin plugin;
    private final FusionEngine engine;
    private final int cost;
    private final Logger log;
    private final boolean debug;

    /** Players with a fusion chest open -> the machine block that opened it. */
    private final Map<UUID, Location> openChests = new HashMap<>();

    public FusionChestMenu(Plugin plugin, FusionEngine engine, int cost, Logger log, boolean debug) {
        this.plugin = plugin;
        this.engine = engine;
        this.cost = cost;
        this.log = log;
        this.debug = debug;
    }

    public void open(Player player, Location machine) {
        Inventory inv = Bukkit.createInventory(null, SIZE, plain("✦ Fusion", NamedTextColor.GOLD));
        inv.setItem(ARROW_SLOT, icon(Material.ARROW, "→", NamedTextColor.GRAY));
        inv.setItem(RESULT_SLOT, emptyResultFiller());
        for (int i = 4; i < SIZE; i++) {
            inv.setItem(i, icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        }
        player.openInventory(inv);
        openChests.put(player.getUniqueId(), machine);
    }

    public boolean isOpenFor(Player player) {
        return openChests.containsKey(player.getUniqueId());
    }

    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !openChests.containsKey(player.getUniqueId())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        boolean topClicked = raw >= 0 && raw < SIZE;

        if (topClicked && raw != TARGET_SLOT && raw != INGREDIENT_SLOT && raw != RESULT_SLOT) {
            event.setCancelled(true); // arrow + filler: decorative, untouchable
            return;
        }

        if (topClicked && raw == RESULT_SLOT) {
            event.setCancelled(true);
            take(player, top);
            return;
        }

        // Target/Ingredient slot: let the click apply (move/place/swap as
        // normal), then recompute the preview once the inventory has settled
        // — mid-event the slot still holds its pre-click contents.
        if (topClicked || event.isShiftClick()) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshPreview(top));
        }
    }

    public void onClose(InventoryCloseEvent event) {
        HumanEntity who = event.getPlayer();
        Location machine = openChests.remove(who.getUniqueId());
        if (machine == null || !(who instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        giveBack(player, top.getItem(TARGET_SLOT));
        giveBack(player, top.getItem(INGREDIENT_SLOT));
    }

    private void refreshPreview(Inventory inv) {
        ItemStack target = inv.getItem(TARGET_SLOT);
        ItemStack ingredient = inv.getItem(INGREDIENT_SLOT);
        if (isEmpty(target) || isEmpty(ingredient)) {
            inv.setItem(RESULT_SLOT, emptyResultFiller());
            return;
        }
        FusionResult result = engine.fuse(target, ingredient);
        inv.setItem(RESULT_SLOT, result.success() ? result.output() : hintBarrier(result.message()));
    }

    private void take(Player player, Inventory inv) {
        ItemStack target = inv.getItem(TARGET_SLOT);
        ItemStack ingredient = inv.getItem(INGREDIENT_SLOT);
        if (isEmpty(target) || isEmpty(ingredient)) {
            return;
        }

        FusionResult result = engine.fuse(target, ingredient);
        if (!result.success()) {
            logTake(player, target, ingredient, "refused(" + result.message() + ")");
            return;
        }
        if (cost > 0 && player.getLevel() < cost) {
            player.sendMessage(plain("Fusing costs " + cost + " XP levels.", NamedTextColor.RED));
            logTake(player, target, ingredient, "refused(needs " + cost + " levels)");
            return;
        }
        if (cost > 0) {
            player.giveExpLevels(-cost);
        }

        deliver(player, result.output());
        consume(inv, TARGET_SLOT);
        consume(inv, INGREDIENT_SLOT);
        inv.setItem(RESULT_SLOT, emptyResultFiller());
        player.updateInventory();
        playEffects(openChests.get(player.getUniqueId()));
        logTake(player, target, ingredient, "committed");
    }

    private void logTake(Player player, ItemStack a, ItemStack b, String outcome) {
        if (debug) {
            log.info("[fusion] chest take by " + player.getName() + ": "
                    + desc(a) + " + " + desc(b) + " => " + outcome);
        }
    }

    /** A non-takeable red barrier explaining, in the result slot, why a fusion can't happen. */
    private ItemStack hintBarrier(String message) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Can't fuse: " + message, NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private void giveBack(Player player, ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

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
        if (loc == null) {
            return;
        }
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Location center = loc.clone().add(0.5, 1.0, 0.5);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 40, 0.4, 0.6, 0.4, 0.2);
        world.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
    }

    private static ItemStack icon(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(name, color));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The result slot's placeholder when there's nothing to preview. Kept
     * non-null (unlike a bare empty slot) so a shift-click from the player's
     * own inventory can never land here once Target/Ingredient are filled —
     * it would otherwise be the "next empty slot" and get silently clobbered
     * by the next preview refresh.
     */
    private static ItemStack emptyResultFiller() {
        return icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "Result", NamedTextColor.GRAY);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static String desc(ItemStack item) {
        return isEmpty(item) ? "(empty)" : item.getType().name();
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
