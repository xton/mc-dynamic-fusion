package com.xton.fusion.machine;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Wires Fusion Machine blocks to the GUI. A machine is marked by a PDC flag on
 * the placed block's own block-entity (no side file), so the marker can never
 * drift from the block: place tags it, break drops the machine item back, and
 * right-click opens the fusion (anvil) GUI.
 */
public final class MachineListener implements Listener {

    private final FusionMachineMenu menu;

    public MachineListener(FusionMachineMenu menu) {
        this.menu = menu;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (menu.isMachineItem(event.getItemInHand())) {
            menu.tagBlock(event.getBlock());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!menu.isMachineBlock(block)) {
            return;
        }
        // Give the machine item back instead of a plain enchanting table.
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                menu.createMachineItem());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return; // allow placing blocks against the machine while sneaking
        }
        Block block = event.getClickedBlock();
        if (!menu.isMachineBlock(block)) {
            return;
        }
        event.setCancelled(true); // suppress the vanilla enchanting-table UI
        menu.open(player, block.getLocation());
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        menu.onPrepare(event);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        menu.onClick(event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        menu.onClose(event);
    }
}
