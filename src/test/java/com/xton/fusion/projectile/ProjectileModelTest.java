package com.xton.fusion.projectile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.ModifierContext;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ModifierStack;
import com.xton.fusion.modifier.impl.ExpandModifier;
import com.xton.fusion.modifier.impl.LifetimeModifier;
import com.xton.fusion.modifier.impl.MiningModifier;
import com.xton.fusion.modifier.impl.MultishotModifier;
import com.xton.fusion.modifier.impl.NovaModifier;
import com.xton.fusion.modifier.impl.PierceModifier;
import com.xton.fusion.modifier.impl.SpreadModifier;

/**
 * The projectile spec is built purely by {@link ProjectileLauncher#buildContext}
 * (no world), so the Noita-style example builds are unit-testable: we assert the
 * primitives each recipe composes into.
 */
class ProjectileModelTest {

    /** A launcher whose world-touching deps are unused by buildContext. */
    private ProjectileLauncher launcher() {
        return new ProjectileLauncher(null, null, new ProjectileLauncher.Settings(
                2.5, 1.0, 1.6, 30, 3.0));
    }

    private ModifierRegistry registry() {
        return new ModifierRegistry()
                .register(new NovaModifier(4.0, 1.4))
                .register(new ExpandModifier(3.0))
                .register(new MultishotModifier(2))
                .register(new SpreadModifier(12.0))
                .register(new PierceModifier())
                .register(new LifetimeModifier(30))
                .register(new MiningModifier(6, 2.5, 3.0));
    }

    private ModifierContext build(String... ids) {
        ModifierStack stack = registry().resolve(List.of(ids));
        return launcher().buildContext(stack);
    }

    private Payload payload(String... ids) {
        ProjectileLauncher launcher = launcher();
        return launcher.buildPayload(launcher.buildContext(registry().resolve(List.of(ids))));
    }

    @Test
    void baseSpecComesFromSettings() {
        ModifierContext ctx = build();
        assertEquals(1, ctx.getCount());
        assertEquals(0.0, ctx.getSpreadDegrees(), 1.0e-9);
        assertEquals(1.6, ctx.getSpeed(), 1.0e-9);
        assertEquals(30, ctx.getLifetimeTicks());
        assertEquals(3.0, ctx.getPierceMaxHardness(), 1.0e-9);
        assertFalse(ctx.isPierce());
        assertFalse(ctx.isMining());
    }

    @Test
    void shotgunIsMultishotPlusSpread() {
        ModifierContext ctx = build("MULTISHOT", "MULTISHOT", "SPREAD");
        assertEquals(5, ctx.getCount());              // 1 + 2 + 2
        assertEquals(12.0, ctx.getSpreadDegrees(), 1.0e-9);
    }

    @Test
    void rayGunPiercesWithExtendedLifetime() {
        ModifierContext ctx = build("PIERCE", "LIFETIME");
        assertTrue(ctx.isPierce());
        assertEquals(60, ctx.getLifetimeTicks());     // base 30 + LIFETIME 30
    }

    @Test
    void miningOverridesSpeedAndLifetime() {
        ModifierContext ctx = build("MINING");
        assertTrue(ctx.isMining());
        assertTrue(ctx.isPierce());
        assertEquals(6, ctx.getLifetimeTicks());      // MINING sets a short life
        assertEquals(2.5, ctx.getSpeed(), 1.0e-9);
    }

    @Test
    void novaStillSeedsTheBurst() {
        ModifierContext ctx = build("NOVA");
        assertTrue(ctx.isRadial());
        assertEquals(4.0, ctx.getRadius(), 1.0e-9);
        assertEquals(1.4, ctx.getPower(), 1.0e-9);
    }

    // ----- payload: burst is opt-in, so mining/pierce alone deliver nothing -----

    @Test
    void burstIsOptInPerModifier() {
        assertFalse(build().hasBurst(), "a bare shot delivers no burst");
        assertFalse(build("MINING").hasBurst(), "a mining ray delivers no burst");
        assertFalse(build("PIERCE").hasBurst(), "a kinetic lance delivers no burst");
        assertTrue(build("NOVA").hasBurst());
        assertTrue(build("EXPAND").hasBurst());
        assertTrue(build("MULTISHOT", "NOVA").hasBurst());
    }

    @Test
    void miningRayHasEmptyPayload() {
        assertTrue(payload("MINING").isEmpty(), "no pop at a mining ray's terminus");
        assertTrue(payload("PIERCE").isEmpty());
        assertTrue(payload().isEmpty());
    }

    @Test
    void burstShotHasNonEmptyPayload() {
        assertFalse(payload("NOVA").isEmpty());
        assertFalse(payload("MINING", "NOVA").isEmpty(), "mining + nova still bursts");
    }
}
