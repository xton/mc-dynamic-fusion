package com.xton.fusion.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.NovaModifier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Pure: Adventure components build without a server. */
class LoreGeneratorTest {

    private String plain(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    @Test
    void rendersHeaderModifierAndProvenance() {
        ModifierRegistry registry = new ModifierRegistry().register(new NovaModifier());
        LoreGenerator lore = new LoreGenerator(registry);

        List<Component> lines = lore.generate(List.of("NOVA"), 1, "Diamond Sword + Nether Star");
        List<String> text = lines.stream().map(this::plain).toList();

        assertTrue(text.stream().anyMatch(s -> s.contains("Gen 1")), text.toString());
        assertTrue(text.stream().anyMatch(s -> s.contains("Nova")), text.toString());
        assertTrue(text.stream().anyMatch(s -> s.contains("Fused from: Diamond Sword + Nether Star")),
                text.toString());
    }

    @Test
    void unknownModifierIdsAreSkipped() {
        ModifierRegistry registry = new ModifierRegistry().register(new NovaModifier());
        LoreGenerator lore = new LoreGenerator(registry);

        List<Component> lines = lore.generate(List.of("NOVA", "MYSTERY"), 2, "A + B");
        long novaLines = lines.stream().map(this::plain).filter(s -> s.contains("Nova")).count();

        assertEquals(1, novaLines);
    }
}
