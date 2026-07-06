package com.xton.fusion.wearable;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.util.WorldFilter;

/**
 * A fused LIFT chestplate/elytra is a jetpack, not a glider: block the vanilla
 * elytra glide from ever engaging, so its own look-tied automatic forward
 * momentum never fights {@link JetpackTask}'s directional thruster control.
 * Non-LIFT elytras are untouched — they glide exactly as vanilla.
 */
public final class JetpackGlideListener implements Listener {

    private final FusedItemReader reader;
    private final WorldFilter worldFilter;

    public JetpackGlideListener(FusedItemReader reader, WorldFilter worldFilter) {
        this.reader = reader;
        this.worldFilter = worldFilter;
    }

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) {
            return; // only block the transition INTO gliding; let it turn off normally
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (worldFilter.isAllowed(player.getWorld()) && WornLift.isWorn(reader, player)) {
            event.setCancelled(true);
        }
    }
}
