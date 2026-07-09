package com.xton.fusion.wearable;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.util.WorldFilter;

/**
 * {@link JetpackTask} grants a survival LIFT wearer {@code AllowFlight} purely
 * to silence the server's anti-fly kick — that permission is also what lets
 * the client's double-tap-space gesture toggle <em>real</em> creative-style
 * flight, which suppresses gravity and fall damage outright (the jetpack's
 * thrust, not an immunity). Block that transition outright, the same "cancel
 * the toggle into the state that would fight the jetpack" treatment
 * {@link JetpackGlideListener} already gives vanilla gliding — real flight
 * never gets a chance to engage in the first place, rather than being reacted
 * to (and briefly live) after the fact.
 *
 * <p>A creative/spectator player's own legitimate flight is never touched,
 * whether or not they happen to be wearing a LIFT item — matching
 * {@link JetpackTask}'s own AllowFlight bookkeeping, which leaves them alone
 * too.
 */
public final class JetpackFlightListener implements Listener {

    private final FusedItemReader reader;
    private final WorldFilter worldFilter;

    public JetpackFlightListener(FusedItemReader reader, WorldFilter worldFilter) {
        this.reader = reader;
        this.worldFilter = worldFilter;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) {
            return; // only block the transition INTO real flight; let it turn off normally
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return; // never touch a creative/spectator player's own legitimate flight
        }
        if (worldFilter.isAllowed(player.getWorld()) && WornLift.isWorn(reader, player)) {
            event.setCancelled(true);
        }
    }
}
