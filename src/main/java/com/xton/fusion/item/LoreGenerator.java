package com.xton.fusion.item;

import java.util.ArrayList;
import java.util.List;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierRegistry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Builds Adventure lore for a fused item. Pure — Adventure components are
 * constructed without any server, so this is unit-testable.
 */
public final class LoreGenerator {

    private final ModifierRegistry registry;

    public LoreGenerator(ModifierRegistry registry) {
        this.registry = registry;
    }

    public List<Component> generate(List<String> modifierIds, String fusedFrom) {
        List<Component> lore = new ArrayList<>();

        lore.add(plain("✦ Fusion Weapon", NamedTextColor.GOLD));
        lore.add(separator());

        for (String id : modifierIds) {
            Modifier modifier = registry.get(id).orElse(null);
            if (modifier == null) {
                continue;
            }
            Component line = plain("✦ " + modifier.displayName() + " ", NamedTextColor.AQUA)
                    .append(plain("— " + modifier.description(), NamedTextColor.GRAY))
                    .hoverEvent(plain(modifier.detailedDescription(), NamedTextColor.GRAY));
            lore.add(line);
        }

        lore.add(separator());
        lore.add(plain("Fused from: " + fusedFrom, NamedTextColor.GRAY));
        return lore;
    }

    private static Component separator() {
        return plain("─────────", NamedTextColor.DARK_GRAY);
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
