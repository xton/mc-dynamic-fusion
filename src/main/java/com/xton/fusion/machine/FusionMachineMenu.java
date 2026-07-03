package com.xton.fusion.machine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
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
    private final Logger log;
    private final boolean debug;

    /** Players with a fusion anvil open → the machine block that opened it. */
    private final Map<UUID, Location> openAnvils = new HashMap<>();
    /** Last preview line logged per player, to log one line per distinct attempt. */
    private final Map<UUID, String> lastLogged = new HashMap<>();

    public FusionMachineMenu(FusionEngine engine, FusionKeys keys, int cost, Logger log, boolean debug) {
        this.engine = engine;
        this.keys = keys;
        this.cost = cost;
        this.log = log;
        this.debug = debug;
    }

    // ----- machine item + block -----

    public ItemStack createMachineItem() {
        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
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

    /** Mark a freshly placed block as a Fusion Machine (block-entity PDC, no side file). */
    public void tagBlock(Block block) {
        if (block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(keys.machine, PersistentDataType.BYTE, (byte) 1);
            tile.update();
        }
    }

    public boolean isMachineBlock(Block block) {
        if (block.getState() instanceof TileState tile) {
            Byte flag = tile.getPersistentDataContainer().get(keys.machine, PersistentDataType.BYTE);
            return flag != null && flag == (byte) 1;
        }
        return false;
    }

    // ----- opening -----

    public void open(Player player, Location machine) {
        // Open a titled anvil view ("✦ Fusion" instead of the vanilla "Repair &
        // Name"). checkReachable(false) is the equivalent of the old
        // openAnvil(null, true) — no real anvil block required.
        AnvilView view = MenuType.ANVIL.builder()
                .title(plain("✦ Fusion", NamedTextColor.GOLD))
                .checkReachable(false)
                .build(player);
        player.openInventory(view);
        openAnvils.put(player.getUniqueId(), machine);
    }

    // ----- event handling (routed by MachineListener) -----

    /** Compute the fusion result for the anvil's two inputs as a live preview. */
    @SuppressWarnings("deprecation") // AnvilInventory#setRepairCost is the reliable cross-version call
    public void onPrepare(PrepareAnvilEvent event) {
        HumanEntity viewer = event.getView().getPlayer();
        if (!openAnvils.containsKey(viewer.getUniqueId())) {
            return;
        }
        AnvilInventory inv = event.getInventory();
        ItemStack target = inv.getItem(0);
        ItemStack ingredient = inv.getItem(1);
        AnvilView anvilView = event.getView() instanceof AnvilView av ? av : null;

        // Target present but no ingredient: this is a rename-only operation, not
        // a fusion. Offer the item back (renamed if a name was typed) rather than
        // an empty result — leaving it null makes the vanilla rename preview
        // flicker through for a frame. A takeable result is what the rename field
        // implies.
        boolean renameOnly = !isEmpty(target) && isEmpty(ingredient);
        FusionResult result = renameOnly ? null : engine.fuse(target, ingredient);

        if (renameOnly) {
            event.setResult(renamedCopy(target, anvilView));
        } else if (result.success()) {
            event.setResult(applyRename(result.output(), anvilView));
        } else if (!isEmpty(target) && !isEmpty(ingredient)) {
            // Both inputs present but they can't fuse — say why right in the
            // result slot. (An action bar is invisible while the anvil GUI is
            // open, so the message has to live inside the GUI.)
            event.setResult(hintBarrier(result.message()));
        } else {
            event.setResult(null); // still mid-setup, nothing to explain
        }
        // Clear the vanilla level cost so the result is takeable in survival (we
        // charge our own XP). Set it on the AnvilInventory directly — relying on
        // the view being an AnvilView was unreliable and left the cost in place,
        // so the anvil blocked the result as "Too Expensive".
        inv.setRepairCost(0);

        if (debug) {
            String outcome = renameOnly ? "RENAME"
                    : (result.success() ? "OK" : "REFUSED(" + result.message() + ")");
            String line = "anvil preview by " + viewer.getName() + ": " + desc(target) + " + "
                    + desc(ingredient) + " => " + outcome
                    + " [view=" + event.getView().getClass().getSimpleName()
                    + ", isAnvilView=" + (anvilView != null) + "]";
            if (!line.equals(lastLogged.put(viewer.getUniqueId(), line))) {
                log.info("[fusion] " + line);
            }
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

        ItemStack a = inv.getItem(0);
        ItemStack b = inv.getItem(1);
        AnvilView view = event.getView() instanceof AnvilView av ? av : null;

        // Rename-only take: a target, no ingredient. Hand back the item (renamed
        // if a name was typed), consuming nothing but the target slot. No XP —
        // it isn't a fusion.
        if (!isEmpty(a) && isEmpty(b)) {
            deliver(player, renamedCopy(a, view), false);
            consume(inv, 0);
            inv.setItem(RESULT_SLOT, null);
            player.updateInventory();
            logTake(player, a, b, "renamed");
            return;
        }

        FusionResult result = engine.fuse(a, b);
        if (!result.success()) {
            logTake(player, a, b, "refused(" + result.message() + ")");
            return;
        }
        if (cost > 0 && player.getLevel() < cost) {
            player.sendMessage(plain("Fusing costs " + cost + " XP levels.", NamedTextColor.RED));
            logTake(player, a, b, "refused(needs " + cost + " levels)");
            return;
        }
        if (cost > 0) {
            player.giveExpLevels(-cost);
        }

        // Re-apply the rename so the *taken* item keeps the custom name, not just
        // the preview.
        deliver(player, applyRename(result.output(), view), true);
        consume(inv, 0); // Target is upgraded into the result
        consume(inv, 1); // Ingredient is spent
        inv.setItem(RESULT_SLOT, null);
        player.updateInventory();
        playEffects(machine);
        logTake(player, a, b, "committed");
    }

    private void logTake(Player player, ItemStack a, ItemStack b, String outcome) {
        if (debug) {
            log.info("[fusion] anvil take by " + player.getName() + ": "
                    + desc(a) + " + " + desc(b) + " => " + outcome);
        }
    }

    public void onClose(InventoryCloseEvent event) {
        openAnvils.remove(event.getPlayer().getUniqueId());
        lastLogged.remove(event.getPlayer().getUniqueId());
    }

    // ----- internals -----

    /** Apply the anvil's rename text to the fused output, if any was typed. */
    private ItemStack applyRename(ItemStack output, AnvilView view) {
        String rename = view != null ? view.getRenameText() : null;
        if (rename != null && !rename.isBlank()) {
            ItemMeta meta = output.getItemMeta();
            meta.displayName(Component.text(rename).decoration(TextDecoration.ITALIC, false));
            output.setItemMeta(meta);
        }
        return output;
    }

    /** A non-takeable red barrier that explains, in the result slot, why a fusion can't happen. */
    private ItemStack hintBarrier(String message) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Can't fuse: " + message, NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static String desc(ItemStack item) {
        return isEmpty(item) ? "(empty)" : item.getType().name();
    }

    private void deliver(Player player, ItemStack output, boolean announce) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(output);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        if (announce) {
            player.sendMessage(plain("✦ Fusion complete!", NamedTextColor.GREEN));
        }
    }

    /** A clone of {@code target} with the anvil's rename applied (if any was typed). */
    private ItemStack renamedCopy(ItemStack target, AnvilView view) {
        return applyRename(target.clone(), view);
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
