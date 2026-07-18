package com.xton.fusion.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.fusion.FusionResult;
import com.xton.fusion.item.FusedItemFactory;
import com.xton.fusion.item.LatentRegistry;
import com.xton.fusion.machine.FusionMachineMenu;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ParameterizedModifier;
import com.xton.fusion.selftest.SelfTest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Admin command (op-only):
 * <ul>
 *   <li>{@code /fusion machine} — get a Fusion Machine block.</li>
 *   <li>{@code /fusion fuse} — fuse main-hand (Target) with off-hand (Ingredient).</li>
 *   <li>{@code /fusion give <player> <base> <MOD...>} — build a fused weapon directly.</li>
 * </ul>
 */
public final class FusionCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("machine", "fuse", "give", "test", "showcase");
    private static final List<String> BASE_HINTS =
            List.of("DIAMOND_SWORD", "NETHERITE_SWORD", "DIAMOND_PICKAXE", "BOW", "DIAMOND_AXE", "TRIDENT",
                    "BRUSH", "ELYTRA", "DIAMOND_CHESTPLATE", "DIAMOND_HELMET");

    private final FusionMachineMenu menu;
    private final ModifierRegistry registry;
    private final LatentRegistry latent;
    private final FusedItemFactory factory;
    private final FusionEngine engine;
    private final int cost;
    private final Logger log;
    private final boolean debug;
    private final SelfTest selfTest;

    public FusionCommand(FusionMachineMenu menu, ModifierRegistry registry, LatentRegistry latent,
                         FusedItemFactory factory, FusionEngine engine, int cost,
                         Logger log, boolean debug, SelfTest selfTest) {
        this.menu = menu;
        this.registry = registry;
        this.latent = latent;
        this.factory = factory;
        this.engine = engine;
        this.cost = cost;
        this.log = log;
        this.debug = debug;
        this.selfTest = selfTest;
    }

    private void logFuse(Player player, ItemStack target, ItemStack ingredient, String outcome) {
        if (debug) {
            log.info("[fusion] /fusion fuse by " + player.getName() + ": "
                    + (target == null ? "(empty)" : target.getType().name()) + " + "
                    + (ingredient == null ? "(empty)" : ingredient.getType().name())
                    + " => " + outcome);
        }
    }

    /**
     * Filter args to known modifier IDs (upper-cased), skipping anything unknown.
     * A {@code from:<item>} token expands to all the modifiers that item carries in
     * the latent registry (e.g. {@code from:tnt} → DAMAGE EXPAND EXPAND), composing
     * with literal IDs in the same list.
     */
    public static List<String> knownIds(ModifierRegistry registry, LatentRegistry latent, String[] args, int from) {
        List<String> ids = new ArrayList<>();
        for (int i = from; i < args.length; i++) {
            String arg = args[i];
            if (arg.regionMatches(true, 0, "from:", 0, 5) && arg.length() > 5) {
                expandFrom(registry, latent, arg.substring(5), ids);
                continue;
            }
            String id = arg.toUpperCase(Locale.ROOT);
            if (registry.isKnown(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Append the modifiers the named item carries in the latent registry (known ones only). */
    private static void expandFrom(ModifierRegistry registry, LatentRegistry latent, String itemName, List<String> out) {
        if (latent == null) {
            return;
        }
        Material mat = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
        if (mat == null) {
            return;
        }
        for (String id : latent.get(mat)) {
            if (registry.isKnown(id)) {
                out.add(id);
            }
        }
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("test")) {
            selfTest.run(sender);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("showcase")) {
            return showcase(sender);
        }
        sender.sendMessage(Component.text(
                "Usage: /fusion machine | /fusion fuse | /fusion give <player> <base> <MODIFIER...>"
                        + " | /fusion test",
                NamedTextColor.GRAY));
        return true;
    }

    /**
     * Fill chests in front of the player with a labelled showcase of every notable
     * build (see {@link Showcase}) — a fast start for manual UAT: grab the item the
     * checklist names by its display name and swing.
     */
    private boolean showcase(CommandSender sender) {
        Player player = resolvePlayer(sender);
        if (player == null) {
            sender.sendMessage(Component.text("Only a player can spawn the showcase chests.", NamedTextColor.RED));
            return true;
        }
        List<Showcase.Entry> roster = Showcase.roster();
        List<ItemStack> items = new ArrayList<>(roster.size() + 1);
        for (Showcase.Entry e : roster) {
            ItemStack item = factory.create(e.base(), e.modifiers(), "showcase");
            item.editMeta(meta -> meta.displayName(Component.text(e.name(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)));
            items.add(item);
        }
        // The roster includes fused bows, which are useless without ammunition —
        // one shared stack covers all of them (any arrows in inventory work).
        if (roster.stream().anyMatch(e -> e.base() == Material.BOW)) {
            items.add(new ItemStack(Material.ARROW, 64));
        }

        // A row of chests two blocks in front, extending to the player's right.
        BlockFace facing = player.getFacing();
        BlockFace right = rightOf(facing);
        Block origin = player.getLocation().getBlock().getRelative(facing, 2);
        int perChest = 27;
        int chestCount = (items.size() + perChest - 1) / perChest;
        int placed = 0;
        for (int c = 0; c < chestCount; c++) {
            Block block = origin.getRelative(right, c);
            block.setType(Material.CHEST);
            if (block.getState() instanceof Chest chest) {
                for (int i = 0; i < perChest && placed < items.size(); i++, placed++) {
                    chest.getInventory().addItem(items.get(placed));
                }
            }
        }
        player.sendMessage(Component.text("Placed " + chestCount + " showcase chest(s) — "
                + roster.size() + " labelled weapons — in front of you.", NamedTextColor.GREEN));
        return true;
    }

    /** The block face 90° clockwise from {@code facing} (its right-hand side). */
    private static BlockFace rightOf(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    /**
     * The player behind {@code sender}, unwrapping the {@link ProxiedCommandSender} that
     * Bukkit substitutes when the command runs via {@code /execute as <player> run ...}
     * (its callee is the acting player; {@code sender} itself is never a {@link Player}
     * in that case).
     */
    private static Player resolvePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        if (sender instanceof ProxiedCommandSender proxied && proxied.getCallee() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean giveMachine(CommandSender sender) {
        Player player = resolvePlayer(sender);
        if (player == null) {
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
        Player player = resolvePlayer(sender);
        if (player == null) {
            sender.sendMessage("Only players can fuse.");
            return true;
        }
        ItemStack target = player.getInventory().getItemInMainHand();
        ItemStack ingredient = player.getInventory().getItemInOffHand();

        FusionResult result = engine.fuse(target, ingredient);
        if (!result.success()) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            logFuse(player, target, ingredient, "refused(" + result.message() + ")");
            return true;
        }
        if (cost > 0 && player.getLevel() < cost) {
            player.sendMessage(Component.text("Fusing costs " + cost + " XP levels.", NamedTextColor.RED));
            logFuse(player, target, ingredient, "refused(needs " + cost + " levels)");
            return true;
        }
        if (cost > 0) {
            player.giveExpLevels(-cost);
        }

        player.getInventory().setItemInMainHand(result.output());
        ingredient.setAmount(ingredient.getAmount() - 1);
        player.getInventory().setItemInOffHand(ingredient.getAmount() <= 0 ? null : ingredient);

        player.sendMessage(Component.text("✦ Fusion complete!", NamedTextColor.GREEN));
        logFuse(player, target, ingredient, "committed");
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
        List<String> ids = knownIds(registry, latent, args, 3);
        if (ids.isEmpty()) {
            sender.sendMessage(Component.text("No known modifiers given. Known: "
                    + String.join(", ", registry.ids()), NamedTextColor.RED));
            return true;
        }
        ItemStack item = factory.create(base, ids, "/fusion give");
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
                return completeModifierArg(args[args.length - 1]);
            }
        }
        return List.of();
    }

    /**
     * Complete a modifier-slot argument. Once it contains a colon (e.g.
     * {@code "MOB:CO"}), suggest completions for the parameter instead of the
     * base ID list — {@code from:} pulls from the latent registry's known
     * materials, every other parameterized modifier supplies its own
     * candidates (see {@link com.xton.fusion.modifier.ParameterizedModifier#parameterCompletions}).
     */
    private List<String> completeModifierArg(String current) {
        int colon = current.indexOf(':');
        if (colon < 0) {
            List<String> bases = new ArrayList<>(registry.ids());
            bases.add("FROM");
            return filter(bases, current);
        }
        String base = current.substring(0, colon);
        String param = current.substring(colon + 1);
        List<String> candidates = base.equalsIgnoreCase("from") ? fromCompletions(param) : parameterCompletions(base, param);
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            out.add(base + ":" + candidate);
        }
        return out;
    }

    /** Material names the latent registry actually has an entry for, matching {@code prefix}. */
    private List<String> fromCompletions(String prefix) {
        String p = prefix.toUpperCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (latent != null) {
            for (Material m : latent.entries().keySet()) {
                if (m.name().startsWith(p)) {
                    out.add(m.name());
                }
            }
        }
        return out;
    }

    /** The named modifier's own parameter completions, or empty if it isn't parameterized. */
    private List<String> parameterCompletions(String base, String param) {
        return registry.get(base.toUpperCase(Locale.ROOT))
                .filter(ParameterizedModifier.class::isInstance)
                .map(m -> ((ParameterizedModifier) m).parameterCompletions(param))
                .orElse(List.of());
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
