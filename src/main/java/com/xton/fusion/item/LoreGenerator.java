package com.xton.fusion.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierRegistry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Builds Adventure lore for a fused item. Pure — Adventure components are
 * constructed without any server, so this is unit-testable.
 *
 * <p>Kept legible on deep stacks (up to {@code max-modifiers}): repeated
 * modifiers collapse to one line with a {@code ×N} count, and the "Fused from"
 * provenance is wrapped across lines so a long, varied lineage doesn't run off
 * the edge of the tooltip.
 */
public final class LoreGenerator {

    /** Soft wrap width (plain-text chars) for the "Fused from" provenance. */
    private static final int WRAP_WIDTH = 42;
    private static final String INDENT = "  ";
    private static final String FROM_LABEL = "Fused from: ";

    private final ModifierRegistry registry;

    public LoreGenerator(ModifierRegistry registry) {
        this.registry = registry;
    }

    public List<Component> generate(List<String> modifierIds, String fusedFrom) {
        List<Component> lore = new ArrayList<>();

        lore.add(plain("✦ Fusion Weapon", NamedTextColor.GOLD));
        lore.add(separator());

        // Collapse repeated modifiers into one line with a ×N count (first-
        // appearance order), so a Push×12 weapon isn't twelve identical lines.
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String id : modifierIds) {
            if (registry.get(id).isPresent()) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Modifier modifier = registry.get(e.getKey()).orElseThrow();
            String name = modifier.displayName() + (e.getValue() > 1 ? " ×" + e.getValue() : "");
            lore.add(plain("✦ " + name + " ", NamedTextColor.AQUA)
                    .append(plain("— " + modifier.description(), NamedTextColor.GRAY))
                    .hoverEvent(plain(modifier.detailedDescription(), NamedTextColor.GRAY)));
        }

        lore.add(separator());
        lore.addAll(fusedFromLines(fusedFrom));
        return lore;
    }

    /**
     * The "Fused from" provenance, wrapped across lines at {@link #WRAP_WIDTH}.
     * Breaks only at " + " boundaries; continuation lines are indented.
     */
    private List<Component> fusedFromLines(String fusedFrom) {
        List<Component> out = new ArrayList<>();
        StringBuilder line = new StringBuilder(FROM_LABEL);
        boolean atLineStart = true;
        for (String segment : Lineage.renderSegments(fusedFrom)) {
            int added = (atLineStart ? 0 : 3) + segment.length(); // " + " is 3 chars
            if (!atLineStart && line.length() + added > WRAP_WIDTH) {
                out.add(plain(line.toString(), NamedTextColor.GRAY));
                line = new StringBuilder(INDENT).append(segment);
            } else {
                line.append(atLineStart ? "" : " + ").append(segment);
            }
            atLineStart = false;
        }
        out.add(plain(line.toString(), NamedTextColor.GRAY));
        return out;
    }

    private static Component separator() {
        return plain("─────────", NamedTextColor.DARK_GRAY);
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
