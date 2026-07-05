package com.xton.fusion.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.PushModifier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Pure: Adventure components build without a server. */
class LoreGeneratorTest {

    private String plain(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    @Test
    void rendersHeaderModifierAndProvenance() {
        ModifierRegistry registry = new ModifierRegistry().register(new PushModifier());
        LoreGenerator lore = new LoreGenerator(registry);

        List<Component> lines = lore.generate(List.of("PUSH"), "Diamond Sword + Nether Star");
        List<String> text = lines.stream().map(this::plain).toList();

        assertTrue(text.stream().anyMatch(s -> s.contains("Fusion Weapon")), text.toString());
        assertTrue(text.stream().anyMatch(s -> s.contains("Push")), text.toString());
        assertTrue(text.stream().anyMatch(s -> s.contains("Fused from: Diamond Sword + Nether Star")),
                text.toString());
    }

    @Test
    void parameterizedIdsRenderTheirBoundDisplayName() {
        // A DEPOSIT:DIRT id must resolve through the registry's BASE:PARAM
        // template and render its material-bound name, not fall through as unknown.
        ModifierRegistry registry = new ModifierRegistry()
                .register(new com.xton.fusion.modifier.impl.DepositModifier());
        LoreGenerator lore = new LoreGenerator(registry);

        List<String> text = lore.generate(List.of("DEPOSIT:DIRT"), "A + B").stream()
                .map(this::plain).toList();

        assertTrue(text.stream().anyMatch(s -> s.contains("Deposit Dirt")), text.toString());
    }

    @Test
    void unknownModifierIdsAreSkipped() {
        ModifierRegistry registry = new ModifierRegistry().register(new PushModifier());
        LoreGenerator lore = new LoreGenerator(registry);

        List<Component> lines = lore.generate(List.of("PUSH", "MYSTERY"), "A + B");
        long pushLines = lines.stream().map(this::plain).filter(s -> s.contains("Push")).count();

        assertEquals(1, pushLines);
    }

    @Test
    void deepStackCollapsesModifiersAndWrapsProvenance() {
        ModifierRegistry registry = new ModifierRegistry().register(new PushModifier());
        LoreGenerator lore = new LoreGenerator(registry);

        // A pathological deep fusion: 24 pushes and a long, varied lineage.
        List<String> mods = Collections.nCopies(24, "PUSH");
        List<String> lineage = new ArrayList<>(List.of("Diamond Sword"));
        lineage.addAll(List.of("Nether Star", "Piston", "Heart of the Sea",
                "Magma Cream", "Glowstone Dust", "Blaze Powder", "Echo Shard"));

        List<String> text = lore.generate(mods, Lineage.join(lineage)).stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();

        // The 24 pushes collapse to a single line carrying the ×24 count.
        assertEquals(1, text.stream().filter(s -> s.contains("Push")).count(), text.toString());
        assertTrue(text.stream().anyMatch(s -> s.contains("×24")), text.toString());

        // The long provenance wraps: its ingredients land on more than one line.
        long provenanceLines = text.stream()
                .filter(s -> s.contains("Nether Star") || s.contains("Piston")
                        || s.contains("Heart of the Sea") || s.contains("Echo Shard"))
                .count();
        assertTrue(provenanceLines >= 2, "provenance should wrap across lines: " + text);

        // No lore line runs off the tooltip.
        for (String s : text) {
            assertTrue(s.length() <= 56, "lore line too long (" + s.length() + "): " + s);
        }
    }
}
