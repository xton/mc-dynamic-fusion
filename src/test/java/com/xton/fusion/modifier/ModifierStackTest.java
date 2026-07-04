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

    @Test
    void resolvesParameterizedIdsFromTemplate() {
        // A BASE:PARAM id resolves the template registered under BASE and mints a
        // concrete instance whose own id carries the parameter back (PDC round-trip).
        ModifierRegistry registry = new ModifierRegistry().register(new StubParam());

        assertTrue(registry.isKnown("STUB:X"), "valid param resolves");
        assertFalse(registry.isKnown("STUB:BAD"), "invalid param is unknown");
        assertEquals("STUB:X", registry.get("STUB:X").orElseThrow().id());
        assertEquals(List.of("STUB:X"), registry.resolve(List.of("STUB:X", "STUB:BAD")).ids());
    }

    /** A parameterized modifier that accepts only the parameter {@code X}. */
    private static final class StubParam implements ParameterizedModifier {
        private final String param;

        StubParam() {
            this(null);
        }

        private StubParam(String param) {
            this.param = param;
        }

        @Override
        public Modifier withParameter(String p) {
            return "X".equals(p) ? new StubParam(p) : null;
        }

        @Override
        public String id() {
            return param == null ? "STUB" : "STUB:" + param;
        }

        @Override
        public String displayName() {
            return "Stub";
        }

        @Override
        public String description() {
            return "stub";
        }

        @Override
        public String detailedDescription() {
            return "stub";
        }

        @Override
        public Category category() {
            return Category.EMITTER;
        }

        @Override
        public void apply(WeaponBuilder builder) {
        }
    }
}
