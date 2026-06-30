package com.xton.fusion.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.impl.NovaModifier;

/** Pure: NOVA only sets effect parameters, so no server is required. */
class NovaModifierTest {

    @Test
    void setsRadialRadiusAndPower() {
        NovaModifier nova = new NovaModifier(4.0, 1.4);
        ModifierContext ctx = nova.apply(new ModifierContext());

        assertTrue(ctx.isRadial());
        assertEquals(4.0, ctx.getRadius(), 1.0e-9);
        assertEquals(1.4, ctx.getPower(), 1.0e-9);
    }

    @Test
    void powerCompoundsWithAmplifier() {
        NovaModifier nova = new NovaModifier(4.0, 1.4);
        ModifierContext ctx = new ModifierContext().setAmplifier(2.0);
        nova.apply(ctx);

        assertEquals(2.8, ctx.getPower(), 1.0e-9);
    }
}
