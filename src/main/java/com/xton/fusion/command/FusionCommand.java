package com.xton.fusion.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.fusion.FusionResult;
import com.xton.fusion.item.FusedItemFactory;
import com.xton.fusion.machine.FusionMachineMenu;
import com.xton.fusion.modifier.ModifierRegistry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Admin command (op-only):
 * <ul>
 *   <li>{@code /fusion machine} — get a Fusion Machine block.</li>
 *   <li>{@code /fusion fuse} — fuse main-hand (Target) with off-hand (Ingredient).</li>
 *   <li>{@code /fusion give <player> <base> <MOD...>} — build a fused weapon directly.</li>
 * </ul>
 */
public final class FusionCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("machine", "fuse", "give");
    private static final List<String> BASE_HINTS =
            List.of("DIAMOND_SWORD", "NETHERITE_SWORD", "BOW", "DIAMOND_AXE", "TRIDENT");

    private final FusionMachineMenu menu;
    private final ModifierRegistry registry;
    private final FusedItemFactory factory;
    private final FusionEngine engine;
    private final int cost;

    public FusionCommand(FusionMachineMenu menu, ModifierRegistry registry,
                         FusedItemFactory factory, FusionEngine engine, int cost) {
        this.menu = menu;
        this.registry = registry;
        this.factory = factory;
        this.engine = engine;
        this.cost = cost;
    }

    /** Filter args to known modifier IDs (upper-cased), skipping anything unknown. */
    public static List<String> knownIds(ModifierRegistry registry, String[] args, int from) {
        List<String> ids = new ArrayList<>();
        for (int i = from; i < args.length; i++) {
            String id = args[i].toUpperCase(Locale.ROOT);
            if (registry.isKnown(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("machine")) {
            return giveMachine(sender);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("fuse")) {
            return fuse(sender);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            return give(sender, args);
        }
        sender.sendMessage(Component.text(
                "Usage: /fusion machine | /fusion fuse | /fusion give <player> <base> <MODIFIER...>",
                NamedTextColor.GRAY));
        return true;
    }

    private boolean giveMachine(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can receive a Fusion Machine.");
            return true;
        }
        player.getInventory().addItem(menu.createMachineItem());
        player.sendMessage(Component.text("Gave you a Fusion Machine — place it and right-click.",
                NamedTextColor.GREEN));
        return true;
    }

    /** Quick hand-fusion: main hand = Target (kept), off hand = Ingredient (consumed). */
    private boolean fuse(CommandSender sender) {
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

        player.getInventory().setItemInMainHand(result.output());
        ingredient.setAmount(ingredient.getAmount() - 1);
        player.getInventory().setItemInOffHand(ingredient.getAmount() <= 0 ? null : ingredient);

        player.sendMessage(Component.text("✦ Fusion complete!", NamedTextColor.GREEN));
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 0.4, 0.6, 0.4, 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /fusion give <player> <base> <MODIFIER...>",
                    NamedTextColor.GRAY));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("No online player named " + args[1] + ".", NamedTextColor.RED));
            return true;
        }
        Material base = Material.matchMaterial(args[2]);
        if (base == null) {
            sender.sendMessage(Component.text("Unknown base item: " + args[2] + ".", NamedTextColor.RED));
            return true;
        }
        List<String> ids = knownIds(registry, args, 3);
        if (ids.isEmpty()) {
            sender.sendMessage(Component.text("No known modifiers given. Known: "
                    + String.join(", ", registry.ids()), NamedTextColor.RED));
            return true;
        }
        ItemStack item = factory.create(base, ids, 1, "/fusion give");
        target.getInventory().addItem(item);
        sender.sendMessage(Component.text("Gave " + target.getName() + " a fused "
                + base.name().toLowerCase(Locale.ROOT) + " [" + String.join(", ", ids) + "].",
                NamedTextColor.GREEN));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return filter(names, args[1]);
            }
            if (args.length == 3) {
                return filter(BASE_HINTS, args[2]);
            }
            if (args.length >= 4) {
                return filter(new ArrayList<>(registry.ids()), args[args.length - 1]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toUpperCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toUpperCase(Locale.ROOT).startsWith(p)) {
                out.add(option);
            }
        }
        return out;
    }
}
