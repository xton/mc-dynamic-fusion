package com.xton.fusion.command;

import java.util.List;

import org.bukkit.Material;

/**
 * The canonical roster of showcase weapons for UAT — one per notable build in the
 * manual checklist. {@code /fusion showcase} builds each as a renamed fused item
 * and drops them into chests in front of the tester, so there's no typing.
 *
 * <p>This list is the single source of truth: keep the {@code name}s in sync with
 * {@code docs/uat/manual-checklist.md} so the tester can grab the labelled item
 * the checklist names.
 */
public final class Showcase {

    /** One labelled showcase item: a base to fuse, the modifier IDs, and a display name. */
    public record Entry(Material base, List<String> modifiers, String name) {
    }

    private Showcase() {
    }

    /** The full roster, grouped roughly by theme (order = fill order in the chests). */
    public static List<Entry> roster() {
        return ROSTER;
    }

    private static final List<Entry> ROSTER = List.of(
                // --- classic composed weapons --- (self-centered bursts stay melee
                // on purpose — Nova/Vacuum/Heal Bomb are meant to go off around you)
                new Entry(Material.DIAMOND_SWORD, List.of("PUSH", "EXPAND", "EXPAND"), "Nova"),
                new Entry(Material.DIAMOND_SWORD, List.of("PULL", "EXPAND"), "Vacuum"),
                new Entry(Material.DIAMOND_SWORD, List.of("DAMAGE", "AMPLIFY"), "Fireball"),
                new Entry(Material.DIAMOND_SWORD,
                        List.of("DAMAGE", "MULTISHOT", "SPREAD", "LIFETIME"), "Shotgun"),
                new Entry(Material.DIAMOND_SWORD, List.of("DAMAGE", "PIERCE", "LIFETIME"), "Ray Gun"),
                new Entry(Material.DIAMOND_SWORD, List.of("HEAL", "EXPAND", "AMPLIFY"), "Heal Bomb"),
                // --- mining --- (a bare MINING stack only reaches melee's default
                // arm's length; LIFETIME gives every ray real reach to demo on)
                new Entry(Material.DIAMOND_PICKAXE, List.of("MINING", "PIERCE", "EXPAND", "LIFETIME"), "Mining Laser"),
                new Entry(Material.DIAMOND_PICKAXE,
                        List.of("MINING", "MINING", "MINING", "MINING", "PIERCE", "LIFETIME"), "Obsidian Borer"),
                new Entry(Material.DIAMOND_PICKAXE,
                        List.of("MINING", "PIERCE", "DEPOSIT:DIRT", "LIFETIME"), "Block-Replacer"),
                // --- environmental ---
                new Entry(Material.DIAMOND_SWORD, List.of("FIRE", "PIERCE", "LIFETIME"), "Flamethrower"),
                new Entry(Material.DIAMOND_SWORD, List.of("ICE", "PIERCE", "LIFETIME"), "Frostbrand"),
                new Entry(Material.DIAMOND_SWORD, List.of("DEPOSIT:SAND", "EXPAND", "LIFETIME"), "Sand Burier"),
                new Entry(Material.DIAMOND_SWORD,
                        List.of("DEPOSIT:WATER", "TRAIL", "LIFETIME", "LIFETIME"), "Water Wand"),
                // --- structural / kinetic ---
                new Entry(Material.DIAMOND_SWORD,
                        List.of("DAMAGE", "LIFETIME", "SPAWN", "MULTISHOT", "SPREAD", "FIRE"), "Cluster Firebomb"),
                new Entry(Material.DIAMOND_SWORD,
                        List.of("DAMAGE", "BOUNCE", "DURATION:5", "SPAWN", "MULTISHOT", "SPREAD", "FIRE"),
                        "Bouncing Grenade"),
                new Entry(Material.DIAMOND_SWORD,
                        List.of("PULL", "EXPAND", "LIFETIME", "DELAY:2", "DAMAGE", "AMPLIFY"), "Gravity-Well Grenade"),
                new Entry(Material.DIAMOND_SWORD,
                        List.of("DAMAGE", "GRAVITY", "VISIBLE", "SPEED:0.8", "DURATION:4"), "Mortar Lob"),
                new Entry(Material.DIAMOND_SWORD, List.of("PIERCE", "LIFETIME", "TELEPORT"), "Blink Lance"),
                // --- bows ---
                new Entry(Material.BOW, List.of("DAMAGE", "HOMING", "LIFETIME"), "Homing Bow"),
                new Entry(Material.BOW, List.of("MOB:COW", "MULTISHOT", "SPREAD"), "Cow Launcher"),
                // --- tools & worn ---
                new Entry(Material.BRUSH, List.of("TREASURE", "TREASURE", "TREASURE"), "Golden Brush"),
                new Entry(Material.DIAMOND_HELMET, List.of("GLOW"), "Glow Helmet"),
                new Entry(Material.ELYTRA, List.of("LIFT"), "Jet Elytra"));
}
