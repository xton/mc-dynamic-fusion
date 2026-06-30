package com.xton.fusion.machine;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies our Fusion Machine GUI (so the listener can tell it apart from any
 * other inventory) and remembers which machine block opened it.
 */
public final class FusionMenuHolder implements InventoryHolder {

    public static final int SIZE = 9;
    public static final int TARGET = 0;
    public static final int INGREDIENT = 2;
    public static final int OUTPUT = 4;
    public static final int CONFIRM = 8;

    private final Location machineLocation;
    private Inventory inventory;

    public FusionMenuHolder(Location machineLocation) {
        this.machineLocation = machineLocation;
    }

    public Location getMachineLocation() {
        return machineLocation;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
