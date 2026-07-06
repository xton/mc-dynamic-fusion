package com.xton.fusion.weapon;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.util.WorldFilter;

/**
 * Ambient polish: every tick-period, players holding a fused weapon shed a few
 * subtle particles. Purely cosmetic; scheduled as a repeating task.
 */
public final class ShedParticleTask implements Runnable {

    private final FusedItemReader reader;
    private final WorldFilter worldFilter;

    public ShedParticleTask(FusedItemReader reader, WorldFilter worldFilter) {
        this.reader = reader;
        this.worldFilter = worldFilter;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!reader.isFused(hand) || !worldFilter.isAllowed(player.getWorld())) {
                continue;
            }
            player.getWorld().spawnParticle(Particle.ENCHANT,
                    player.getLocation().add(0, 1.0, 0), 4, 0.2, 0.4, 0.2, 0.02);
        }
    }
}
