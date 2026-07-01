package com.xton.fusion.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.impl.LifetimeModifier;
import com.xton.fusion.modifier.impl.MiningModifier;

/** Pure: LIFETIME/MINING only mutate context, so they test without a server. */
class Phase3ModifiersTest {

    @Test
    void lifetimeAddsTicksAndStacks() {
        ModifierContext ctx = new ModifierContext();
        new LifetimeModifier(30).apply(ctx);
        assertEquals(30, ctx.getLifetimeTicks());
        new LifetimeModifier(30).apply(ctx);
        assertEquals(60, ctx.getLifetimeTicks());
    }

    @Test
    void miningSetsPierceAndSpec() {
        ModifierContext ctx = new ModifierContext();
        assertFalse(ctx.isMining());
        new MiningModifier(6, 2.5, 3.0).apply(ctx);
        assertTrue(ctx.isMining());
        assertTrue(ctx.isPierce(), "a mining ray pierces");
        assertEquals(6, ctx.getLifetimeTicks());
        assertEquals(2.5, ctx.getSpeed(), 1.0e-9);
        assertEquals(3.0, ctx.getPierceMaxHardness(), 1.0e-9);
    }
}
