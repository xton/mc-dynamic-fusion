package com.xton.fusion.command;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.fusion.FusionResult;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Phase Zero fusion entry point: main hand is the Target (kept and upgraded),
 * off hand is the Ingredient (consumed). No GUI yet.
 */
public final class FuseCommand implements CommandExecutor {

    private final FusionEngine engine;
    private final int cost;

    public FuseCommand(FusionEngine engine, int cost) {
        this.engine = engine;
        this.cost = cost;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can fuse.");
            return true;
        }

        ItemStack target = player.getInventory().getItemInMainHand();
        ItemStack ingredient = player.getInventory().getItemInOffHand();

        FusionResult result = engine.fuse(target, ingredient);
        if (!result.success()) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            return true;
        }
        if (cost > 0 && player.getLevel() < cost) {
            player.sendMessage(Component.text("Fusing costs " + cost + " XP levels.", NamedTextColor.RED));
            return true;
        }
        if (cost > 0) {
            player.giveExpLevels(-cost);
        }

        // Target is upgraded in place; Ingredient loses one from the stack.
        player.getInventory().setItemInMainHand(result.output());
        ingredient.setAmount(ingredient.getAmount() - 1);
        player.getInventory().setItemInOffHand(ingredient.getAmount() <= 0 ? null : ingredient);

        player.sendMessage(Component.text("✦ Fusion complete!", NamedTextColor.GREEN));

        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 0.4, 0.6, 0.4, 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        return true;
    }
}
