package com.xton.fusion.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "fused from" provenance of a weapon: the base item followed by every
 * ingredient fused into it, in order. Stored as a delimited string in PDC and
 * rendered collapsed for the tooltip ("Diamond Sword + 3× Nether Star + Heart
 * of the Sea"). Pure and unit-testable.
 */
public final class Lineage {

    /** ASCII unit separator — invisible and never present in an item name. */
    private static final String SEP = "\u001F";

    private Lineage() {
    }

    /** Encode an ordered lineage (base first, then ingredients) for storage. */
    public static String join(List<String> parts) {
        return String.join(SEP, parts);
    }

    /** Decode a stored lineage back to its ordered parts (empty if blank). */
    public static List<String> split(String stored) {
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(stored.split(SEP, -1));
    }

    /**
     * Render for display: the base, then each distinct ingredient with an
     * {@code N×} count (first-appearance order). A single token (a legacy value
     * or the {@code /fusion give} sentinel) renders verbatim.
     */
    public static String render(String stored) {
        return String.join(" + ", renderSegments(stored));
    }

    /**
     * The display pieces of the lineage: the base, then each distinct ingredient
     * with an {@code N×} count (first-appearance order). Callers join these with
     * " + " — or pack them across lines to keep a long tooltip from overflowing.
     */
    public static List<String> renderSegments(String stored) {
        List<String> parts = split(stored);
        if (parts.isEmpty()) {
            return List.of("?");
        }
        if (parts.size() == 1) {
            return List.of(parts.get(0));
        }
        List<String> segments = new ArrayList<>();
        segments.add(parts.get(0));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 1; i < parts.size(); i++) {
            counts.merge(parts.get(i), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            segments.add((e.getValue() > 1 ? e.getValue() + "× " : "") + e.getKey());
        }
        return segments;
    }
}
