package com.xton.fusion.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** Pure: the Golden Brush's proc/loot math needs no server (Material is an enum). */
class GoldenBrushTest {

    private GoldenBrush brush() {
        return new GoldenBrush(new GoldenBrush.Settings(0.15, 0.1, 0.75));
    }

    @Test
    void procChanceScalesWithLevelAndCaps() {
        GoldenBrush b = brush();
        assertEquals(0.0, b.procChance(0), 1.0e-9, "no gold, no proc");
        assertTrue(b.procChance(1) > 0);
        assertTrue(b.procChance(3) > b.procChance(1), "more gold procs more often");
        assertEquals(0.75, b.procChance(100), 1.0e-9, "clamped to the cap");
    }

    @Test
    void higherLevelUnlocksRarerLoot() {
        GoldenBrush b = brush();
        Random rng = new Random(42);

        // DIAMOND is a tier-4 find: a level-1 brush can never roll it.
        boolean diamondAtLow = false;
        for (int i = 0; i < 500; i++) {
            if (b.roll(1, rng) == Material.DIAMOND) {
                diamondAtLow = true;
            }
        }
        assertFalse(diamondAtLow, "level 1 stays in the common tier");

        // A maxed brush can eventually turn one up.
        boolean diamondAtHigh = false;
        for (int i = 0; i < 4000; i++) {
            if (b.roll(GoldenBrush.maxLevel(), rng) == Material.DIAMOND) {
                diamondAtHigh = true;
                break;
            }
        }
        assertTrue(diamondAtHigh, "a maxed brush can roll a rare find");
        assertNotNull(b.roll(1, rng), "a level-1 brush always has common loot");
    }
}
