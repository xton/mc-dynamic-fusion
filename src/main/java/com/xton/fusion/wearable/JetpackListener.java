package com.xton.fusion.wearable;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.impl.LiftModifier;

/**
 * The Jetpack: when a player wearing a fused chestplate/elytra with LIFT jumps,
 * add an upward thrust so they lift off (and, on an elytra, can kick straight
 * into a glide). Uses Paper's {@link PlayerJumpEvent}, so it fires on real jumps
 * only — a controlled hop, not free flight.
 */
public final class JetpackListener implements Listener {

    private final FusedItemReader reader;
    private final double thrust;

    public JetpackListener(FusedItemReader reader, double thrust) {
        this.reader = reader;
        this.thrust = thrust;
    }

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!wearsLift(player)) {
            return;
        }
        Vector v = player.getVelocity();
        // Add to the jump's own upward motion so it clearly boosts rather than replaces it.
        player.setVelocity(v.setY(Math.max(0, v.getY()) + thrust));
    }

    /** True if the chestplate slot (a chestplate or elytra) is fused with LIFT. */
    private boolean wearsLift(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        return chest != null && reader.isFused(chest) && reader.readModifierIds(chest).contains(LiftModifier.ID);
    }
}
