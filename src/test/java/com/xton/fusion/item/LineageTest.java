package com.xton.fusion.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pure: no server needed. */
class LineageTest {

    @Test
    void collapsesDuplicatesInFirstAppearanceOrder() {
        String stored = Lineage.join(
                List.of("Diamond Sword", "Nether Star", "Nether Star", "Heart of the Sea"));
        assertEquals("Diamond Sword + 2× Nether Star + Heart of the Sea", Lineage.render(stored));
    }

    @Test
    void singleTokenRendersVerbatim() {
        assertEquals("/fusion give", Lineage.render("/fusion give"));
    }

    @Test
    void joinSplitRoundTrips() {
        List<String> parts = List.of("A", "B", "B");
        assertEquals(parts, Lineage.split(Lineage.join(parts)));
    }
}
