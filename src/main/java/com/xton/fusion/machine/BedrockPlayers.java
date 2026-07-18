package com.xton.fusion.machine;

import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Detects Bedrock players (via Floodgate) so the Fusion Machine can route them
 * to the chest-based GUI instead of the anvil: Geyser's anvil translation
 * validates a combination against vanilla repair rules client-side and won't
 * reliably render a custom {@code PrepareAnvilEvent} result for a pairing it
 * doesn't recognize (an arbitrary Target+Ingredient fusion, as opposed to a
 * real repair). A plain chest is one of Geyser's most reliably translated
 * container types.
 *
 * <p>Floodgate is a soft dependency (compileOnly, not shaded in) — on a
 * server without it installed, every player is treated as Java/Bedrock-safe
 * and gets the normal anvil GUI.
 *
 * <p>Deliberately not name-prefix matching (Floodgate's default "." prefix on
 * Bedrock usernames): the prefix is server-configurable, and a Bedrock player
 * who has linked their account plays under their plain Java username with no
 * prefix at all, so it would silently miss them.
 */
public final class BedrockPlayers {

    private final boolean floodgatePresent;

    public BedrockPlayers(Plugin plugin, Logger log) {
        this.floodgatePresent = plugin.getServer().getPluginManager().getPlugin("floodgate") != null;
        if (floodgatePresent) {
            log.info("Floodgate detected — Bedrock players get the chest Fusion GUI instead of the anvil.");
        }
    }

    public boolean isBedrock(Player player) {
        if (!floodgatePresent) {
            return false;
        }
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable t) {
            // Defensive: an unexpected Floodgate API mismatch shouldn't break the
            // Fusion Machine for anyone — fall back to treating them as Java.
            return false;
        }
    }
}
