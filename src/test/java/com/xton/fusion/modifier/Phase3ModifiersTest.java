package com.xton.fusion.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.impl.DelayedModifier;
import com.xton.fusion.modifier.impl.MiningModifier;

/** Pure: DELAYED/MINING only mutate context, so they test without a server. */
class Phase3ModifiersTest {

    @Test
    void delayedAddsTicksAndStacks() {
        ModifierContext ctx = new ModifierContext();
        new DelayedModifier(30).apply(ctx);
        assertEquals(30, ctx.getDelayTicks());
        new DelayedModifier(30).apply(ctx);
        assertEquals(60, ctx.getDelayTicks());
    }

    @Test
    void miningSetsFlag() {
        ModifierContext ctx = new ModifierContext();
        assertFalse(ctx.isMining());
        new MiningModifier().apply(ctx);
        assertTrue(ctx.isMining());
    }
}
