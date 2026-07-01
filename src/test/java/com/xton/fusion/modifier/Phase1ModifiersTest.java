package com.xton.fusion.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.impl.ChainModifier;
import com.xton.fusion.modifier.impl.ExpandModifier;
import com.xton.fusion.modifier.impl.MultishotModifier;
import com.xton.fusion.modifier.impl.NovaModifier;

/**
 * Pure: the EXPAND/CHAIN/MULTISHOT modifiers only mutate context, so they test
 * without a server. Verifies each accumulates and that duplicates stack.
 */
class Phase1ModifiersTest {

    @Test
    void expandAddsRadiusAndStacks() {
        ModifierContext ctx = new ModifierContext();
        new ExpandModifier(3.0).apply(ctx);
        assertEquals(3.0, ctx.getExpandBonus(), 1.0e-9);
        new ExpandModifier(3.0).apply(ctx);
        assertEquals(6.0, ctx.getExpandBonus(), 1.0e-9);
    }

    @Test
    void chainAddsHopsAndStacks() {
        ModifierContext ctx = new ModifierContext();
        new ChainModifier(3).apply(ctx);
        new ChainModifier(3).apply(ctx);
        assertEquals(6, ctx.getChainCount());
    }

    @Test
    void multishotAddsCountAndStacks() {
        ModifierContext ctx = new ModifierContext();
        assertEquals(1, ctx.getCount(), "base is one projectile");
        new MultishotModifier(2).apply(ctx);
        new MultishotModifier(2).apply(ctx);
        assertEquals(5, ctx.getCount()); // 1 base + 2 + 2
    }

    @Test
    void resolvedStackComposesAllModifiers() {
        ModifierRegistry registry = new ModifierRegistry()
                .register(new NovaModifier(4.0, 1.4))
                .register(new ExpandModifier(3.0))
                .register(new ChainModifier(3))
                .register(new MultishotModifier(2));

        // Duplicates kept and order preserved (no dedupe).
        ModifierStack stack = registry.resolve(
                List.of("NOVA", "EXPAND", "EXPAND", "CHAIN", "MULTISHOT"));
        ModifierContext ctx = stack.applyTo(new ModifierContext());

        assertTrue(ctx.isRadial());
        assertEquals(4.0, ctx.getRadius(), 1.0e-9);
        assertEquals(6.0, ctx.getExpandBonus(), 1.0e-9); // EXPAND x2
        assertEquals(3, ctx.getChainCount());
        assertEquals(3, ctx.getCount()); // 1 base + 2
    }
}
