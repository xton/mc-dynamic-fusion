package com.xton.fusion.wearable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.modifier.impl.GlowModifier;
import com.xton.fusion.util.WorldFilter;

/**
 * GLOW's other half: the Glowing potion effect ({@link WornEffectTask}) makes
 * <em>other</em> players (or third-person/F5) see the wearer glow through
 * walls — but Minecraft never renders your own body in first person, so a
 * solo wearer sees nothing of their own effect. This gives them something to
 * actually see <em>by</em>: a client-side-only {@link Material#LIGHT} sent
 * just to that player via {@link Player#sendBlockChange}, tracked in front of
 * their eyes. Not a cosmetic illusion — the client's own lighting engine
 * recomputes real brightness from it (the same trick as the DynLight/
 * DynamicLights plugins), so the wearer genuinely sees better in the dark by
 * it, distinct from NIGHT_VISION. The real world is never touched: nothing is
 * written server-side, so there's nothing to grief and nothing left behind on
 * disconnect or shutdown — reverting just means telling that one player what
 * the block there actually is.
 */
public final class GlowLightTask implements Runnable, Listener {

    /** Closest to the player's eyes the light will ever sit — stays "in front of", not inside, the face. */
    private static final double MIN_DISTANCE = 0.3;
    /**
     * Score discount (blocks) the previously-lit cell gets over an equally-good
     * alternative, so a fraction-of-a-degree wobble in look direction doesn't
     * flip the light between two near-tied cells every tick.
     */
    private static final double STABILITY_BONUS = 0.4;

    private final FusedItemReader reader;
    private final WorldFilter worldFilter;
    private final double distance;
    private final Map<UUID, Location> lit = new HashMap<>();

    public GlowLightTask(FusedItemReader reader, WorldFilter worldFilter, double distance) {
        this.reader = reader;
        this.worldFilter = worldFilter;
        this.distance = distance;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        UUID id = player.getUniqueId();
        Location previous = lit.get(id);
        if (!worldFilter.isAllowed(player.getWorld()) || !wearsGlow(player)) {
            if (previous != null) {
                revert(player, previous);
                lit.remove(id);
            }
            return;
        }

        Location target = findLightCell(player);
        if (previous != null && previous.equals(target)) {
            return; // already lighting the right cell for this player
        }
        if (previous != null) {
            revert(player, previous);
        }
        if (target != null) {
            player.sendBlockChange(target, Material.LIGHT.createBlockData());
            lit.put(id, target);
        } else {
            lit.remove(id); // fully enclosed (eyes inside a block) — nowhere nearby to place it
        }
    }

    /**
     * The cell to light: among every open cell within {@code distance} of the
     * player's eyes, whichever is nearest the ideal point straight ahead at
     * that distance (see {@link LightPlacement} for the geometry). An
     * unobstructed view picks directly ahead, same as before; a blocked one
     * backs off to the nearest open cell in <em>any</em> direction, not just
     * forward/back along the line of sight — which is what lets it find
     * somewhere to sit in a corner or under an overhang, where that single
     * line (and its reverse, back through the head) is walled off both ways
     * but open air still exists just to the side. Null only when every cell in
     * range is solid — genuinely embedded, nowhere nearby to put it.
     */
    Location findLightCell(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location previousLoc = lit.get(player.getUniqueId());
        LightPlacement.Cell previous = previousLoc == null ? null
                : new LightPlacement.Cell(previousLoc.getBlockX(), previousLoc.getBlockY(), previousLoc.getBlockZ());

        LightPlacement.Cell chosen = LightPlacement.choose(
                eye.getX(), eye.getY(), eye.getZ(),
                dir.getX(), dir.getY(), dir.getZ(),
                distance, MIN_DISTANCE, previous, STABILITY_BONUS,
                cell -> canLight(new Location(eye.getWorld(), cell.x(), cell.y(), cell.z())));

        return chosen == null ? null : new Location(eye.getWorld(), chosen.x(), chosen.y(), chosen.z());
    }

    /**
     * Fake the light anywhere non-solid — not just true air. A cobweb, tall
     * grass, a torch, still isn't a wall the light would look like it's shining
     * through, and a structure thick with cobwebs (a mineshaft, say) would
     * otherwise starve the search of anywhere at all to put it.
     */
    private boolean canLight(Location loc) {
        World world = loc.getWorld();
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return false;
        }
        if (loc.getBlockY() < world.getMinHeight() || loc.getBlockY() >= world.getMaxHeight()) {
            return false;
        }
        return !loc.getBlock().getType().isSolid();
    }

    /** Show this player what's really there — purely client-side, so this is the entire "cleanup". */
    private void revert(Player player, Location loc) {
        World world = loc.getWorld();
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return;
        }
        player.sendBlockChange(loc, loc.getBlock().getBlockData());
    }

    /** True if any piece of the player's fused armor carries GLOW. */
    private boolean wearsGlow(Player player) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && reader.isFused(piece) && reader.readModifierIds(piece).contains(GlowModifier.ID)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Nothing was ever really placed, so there's no packet worth sending a
        // disconnecting client — just stop tracking them.
        lit.remove(event.getPlayer().getUniqueId());
    }

    /** Best-effort revert for anyone still online (e.g. a plugin reload) — harmless if skipped. */
    public void clearAll() {
        for (Map.Entry<UUID, Location> entry : lit.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                revert(player, entry.getValue());
            }
        }
        lit.clear();
    }
}
