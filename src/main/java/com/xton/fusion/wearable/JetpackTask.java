package com.xton.fusion.wearable;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.impl.LiftModifier;

/**
 * The Jetpack: while airborne and holding jump, a player wearing a fused
 * chestplate/elytra with LIFT rises smoothly, ramping their vertical velocity up
 * to a capped maximum for as long as they keep holding it — a hover/ascend, not
 * a single jump-boost. A normal jump from the ground is untouched (this only
 * acts once the player has left the ground); releasing jump lets gravity take
 * back over immediately.
 *
 * <p>Ticked every tick (not on the coarser worn-effect cadence) so the ramp
 * feels smooth and responsive to the held key, via {@link Player#getCurrentInput()}
 * — Paper's per-tick snapshot of the client's raw movement/jump input, distinct
 * from the discrete {@code PlayerJumpEvent} a single jump fires.
 */
public final class JetpackTask implements Runnable {

    private final FusedItemReader reader;
    private final double thrustPerTick;
    private final double maxVelocity;

    public JetpackTask(FusedItemReader reader, double thrustPerTick, double maxVelocity) {
        this.reader = reader;
        this.thrustPerTick = thrustPerTick;
        this.maxVelocity = maxVelocity;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnGround() || !player.getCurrentInput().isJump() || !wearsLift(player)) {
                continue; // grounded jumps stay vanilla; not holding jump just falls normally
            }
            Vector v = player.getVelocity();
            if (v.getY() < maxVelocity) {
                player.setVelocity(v.setY(Math.min(maxVelocity, v.getY() + thrustPerTick)));
            }
        }
    }

    /** True if the chestplate slot (a chestplate or elytra) is fused with LIFT. */
    private boolean wearsLift(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        return chest != null && reader.isFused(chest) && reader.readModifierIds(chest).contains(LiftModifier.ID);
    }
}
