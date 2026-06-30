package com.xton.fusion.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.impl.NovaModifier;

class ModifierStackTest {

    @Test
    void preservesOrderAndDuplicates() {
        NovaModifier nova = new NovaModifier();
        ModifierStack stack = new ModifierStack(List.of(nova, nova));

        assertEquals(List.of("NOVA", "NOVA"), stack.ids());
        assertTrue(stack.contains("NOVA"));
        assertFalse(stack.contains("AMPLIFY"));
    }

    @Test
    void resolveSkipsUnknownIdsButKeepsOrderAndDuplicates() {
        ModifierRegistry registry = new ModifierRegistry().register(new NovaModifier());
        ModifierStack stack = registry.resolve(List.of("NOVA", "MYSTERY", "NOVA"));

        assertEquals(List.of("NOVA", "NOVA"), stack.ids());
    }
}
