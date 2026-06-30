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
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Wires Fusion Machine blocks to the GUI: registers/forgets machine locations
 * on place/break, opens the menu on right-click, and routes inventory events.
 */
public final class MachineListener implements Listener {

    private final MachineStore store;
    private final FusionMachineMenu menu;

    public MachineListener(MachineStore store, FusionMachineMenu menu) {
        this.store = store;
        this.menu = menu;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (menu.isMachineItem(event.getItemInHand())) {
            store.add(event.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!store.contains(block.getLocation())) {
            return;
        }
        store.remove(block.getLocation());
        // Give the machine item back instead of a plain crafting table.
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
        if (!store.contains(block.getLocation())) {
            return;
        }
        event.setCancelled(true); // suppress the vanilla crafting table UI
        menu.open(player, block.getLocation());
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
