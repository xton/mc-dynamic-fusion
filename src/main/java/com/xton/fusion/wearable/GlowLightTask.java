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

        Location target = frontOfFace(player);
        if (previous != null && previous.equals(target)) {
            return; // already lighting the right cell for this player
        }
        if (previous != null) {
            revert(player, previous);
        }
        if (canLight(target)) {
            player.sendBlockChange(target, Material.LIGHT.createBlockData());
            lit.put(id, target);
        } else {
            lit.remove(id); // facing a solid block up close — skip rather than fake through it
        }
    }

    /** The block-aligned cell {@code distance} blocks in front of the player's eyes. */
    private Location frontOfFace(Player player) {
        Location eye = player.getEyeLocation();
        Vector aim = eye.getDirection().normalize().multiply(distance);
        Location at = eye.clone().add(aim);
        return new Location(at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    /** Only fake the light over real air, so it never looks like it's shining through a solid wall. */
    private boolean canLight(Location loc) {
        World world = loc.getWorld();
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return false;
        }
        if (loc.getBlockY() < world.getMinHeight() || loc.getBlockY() >= world.getMaxHeight()) {
            return false;
        }
        return loc.getBlock().getType() == Material.AIR;
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
