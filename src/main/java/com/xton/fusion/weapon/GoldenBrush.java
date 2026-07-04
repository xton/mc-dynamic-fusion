package com.xton.fusion.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Material;

/**
 * The Golden Brush's loot logic — pure, so it's unit-testable without a server.
 * A loot <em>level</em> (the number of TREASURE/gold on the brush) does two
 * things: it raises the proc chance ({@link #procChance}) and it unlocks rarer
 * tiers in the table ({@link #roll}). Higher tiers are gated behind a minimum
 * level and carry lower weights, so gold makes rare finds <em>possible</em> and
 * gradually more likely, never guaranteed.
 */
public final class GoldenBrush {

    /** One weighted loot entry, available once the brush reaches {@code minLevel}. */
    private record Entry(Material item, int minLevel, double weight) {
    }

    /** Common → legendary. minLevel gates a tier; weight falls as rarity climbs. */
    private static final List<Entry> LOOT = List.of(
            new Entry(Material.DIRT, 1, 20), new Entry(Material.COBBLESTONE, 1, 20),
            new Entry(Material.GRAVEL, 1, 15), new Entry(Material.STICK, 1, 12),
            new Entry(Material.BONE, 1, 10), new Entry(Material.WHEAT_SEEDS, 1, 10),
            new Entry(Material.STRING, 1, 8), new Entry(Material.FLINT, 1, 8),
            new Entry(Material.COAL, 2, 10), new Entry(Material.IRON_NUGGET, 2, 8),
            new Entry(Material.GOLD_NUGGET, 2, 7), new Entry(Material.APPLE, 2, 6),
            new Entry(Material.ARROW, 2, 6), new Entry(Material.LAPIS_LAZULI, 2, 5),
            new Entry(Material.IRON_INGOT, 3, 5), new Entry(Material.GOLD_INGOT, 3, 4),
            new Entry(Material.REDSTONE, 3, 5), new Entry(Material.EMERALD, 3, 3),
            new Entry(Material.EXPERIENCE_BOTTLE, 3, 3),
            new Entry(Material.DIAMOND, 4, 2), new Entry(Material.GOLD_BLOCK, 4, 1),
            new Entry(Material.NETHERITE_SCRAP, 5, 1),
            new Entry(Material.ENCHANTED_GOLDEN_APPLE, 5, 1));

    /** Tunables resolved from config. */
    public record Settings(double procBase, double procPerLevel, double procCap) {
    }

    private final Settings settings;

    public GoldenBrush(Settings settings) {
        this.settings = settings;
    }

    /** Chance a brush stroke at this loot level coughs up an item, clamped to the cap. */
    public double procChance(int level) {
        if (level <= 0) {
            return 0.0;
        }
        return Math.min(settings.procCap(), settings.procBase() + settings.procPerLevel() * level);
    }

    /** Roll a random loot item for {@code level} (rarer tiers unlock with level), or null if none. */
    public Material roll(int level, Random rng) {
        List<Entry> eligible = new ArrayList<>();
        double total = 0;
        for (Entry e : LOOT) {
            if (e.minLevel() <= level) {
                eligible.add(e);
                total += e.weight();
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        double pick = rng.nextDouble() * total;
        for (Entry e : eligible) {
            pick -= e.weight();
            if (pick <= 0) {
                return e.item();
            }
        }
        return eligible.get(eligible.size() - 1).item();
    }

    /** The highest minLevel any entry uses — the loot level at which everything is unlocked. */
    public static int maxLevel() {
        int max = 0;
        for (Entry e : LOOT) {
            max = Math.max(max, e.minLevel());
        }
        return max;
    }
}
