package com.xton.fusion.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.impl.PushModifier;

class ModifierStackTest {

    @Test
    void preservesOrderAndDuplicates() {
        PushModifier push = new PushModifier();
        ModifierStack stack = new ModifierStack(List.of(push, push));

        assertEquals(List.of("PUSH", "PUSH"), stack.ids());
        assertTrue(stack.contains("PUSH"));
        assertFalse(stack.contains("AMPLIFY"));
    }

    @Test
    void resolveSkipsUnknownIdsButKeepsOrderAndDuplicates() {
        ModifierRegistry registry = new ModifierRegistry().register(new PushModifier());
        ModifierStack stack = registry.resolve(List.of("PUSH", "MYSTERY", "PUSH"));

        assertEquals(List.of("PUSH", "PUSH"), stack.ids());
    }
}
