package com.xton.fusion.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.xton.fusion.machine.FusionMachineMenu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** {@code /fusion machine} — gives the player a Fusion Machine block (op-only). */
public final class FusionCommand implements CommandExecutor {

    private final FusionMachineMenu menu;

    public FusionCommand(FusionMachineMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("machine")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can receive a Fusion Machine.");
                return true;
            }
            if (!player.isOp()) {
                player.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
                return true;
            }
            player.getInventory().addItem(menu.createMachineItem());
            player.sendMessage(Component.text("Gave you a Fusion Machine — place it and right-click.",
                    NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /fusion machine", NamedTextColor.GRAY));
        return true;
    }
}
