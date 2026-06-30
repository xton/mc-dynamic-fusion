package com.xton.fusion.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.impl.InvertModifier;
import com.xton.fusion.modifier.impl.PersistModifier;

/** Pure: INVERT/PERSIST only mutate context, so they test without a server. */
class Phase4ModifiersTest {

    @Test
    void invertTogglesAndTwoCancel() {
        ModifierContext ctx = new ModifierContext();
        assertFalse(ctx.isInverted());
        new InvertModifier().apply(ctx);
        assertTrue(ctx.isInverted(), "one INVERT inverts");
        new InvertModifier().apply(ctx);
        assertFalse(ctx.isInverted(), "two INVERTs cancel");
    }

    @Test
    void persistAccumulatesDuration() {
        ModifierContext ctx = new ModifierContext();
        new PersistModifier(60).apply(ctx);
        assertEquals(60, ctx.getPersistTicks());
        new PersistModifier(60).apply(ctx);
        assertEquals(120, ctx.getPersistTicks());
    }
}
