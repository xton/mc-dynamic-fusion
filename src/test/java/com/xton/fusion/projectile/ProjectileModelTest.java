package com.xton.fusion.projectile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ProjectileSpec;
import com.xton.fusion.modifier.WeaponBuilder;
import com.xton.fusion.modifier.impl.DamageModifier;
import com.xton.fusion.modifier.impl.ExpandModifier;
import com.xton.fusion.modifier.impl.LifetimeModifier;
import com.xton.fusion.modifier.impl.MiningModifier;
import com.xton.fusion.modifier.impl.PierceModifier;
import com.xton.fusion.modifier.impl.PushModifier;

/**
 * The launcher builds a projectile's {@link Payload} purely from the compiled
 * spec (one {@link BurstEffect} per AOE emitter), so the flight-vs-payload split
 * is unit-testable: flight-only weapons deliver an empty payload (no pop), while
 * each emitter contributes a burst.
 */
class ProjectileModelTest {

    /** A launcher whose world-touching deps are unused by compile/buildPayload. */
    private ProjectileLauncher launcher() {
        return new ProjectileLauncher(null, null, new WeaponBuilder.Defaults(
                1.6, 30, 3.0, 2.0, 1.0, 2.5, 4.0), 2);
    }

    private ModifierRegistry registry() {
        return new ModifierRegistry()
                .register(new PushModifier())
                .register(new DamageModifier())
                .register(new ExpandModifier(1.6))
                .register(new PierceModifier())
                .register(new LifetimeModifier(30))
                .register(new MiningModifier(6, 2.5, 3.0));
    }

    private Payload payload(String... ids) {
        ProjectileLauncher launcher = launcher();
        ProjectileSpec spec = launcher.compile(registry().resolve(List.of(ids)));
        return launcher.buildPayload(spec);
    }

    @Test
    void flightOnlyWeaponsDeliverEmptyPayload() {
        assertTrue(payload().isEmpty(), "a bare shot delivers nothing");
        assertTrue(payload("MINING").isEmpty(), "a mining ray delivers nothing at its terminus");
        assertTrue(payload("PIERCE", "LIFETIME").isEmpty(), "a kinetic lance delivers nothing");
    }

    @Test
    void eachEmitterContributesABurst() {
        assertFalse(payload("PUSH").isEmpty());
        assertFalse(payload("MINING", "DAMAGE").isEmpty(), "mining + damage still bursts");
    }

    @Test
    void payloadHasOneEffectPerEmitter() {
        // PUSH + DAMAGE = two bursts; EXPAND is a transform, not an emitter.
        ProjectileLauncher launcher = launcher();
        ProjectileSpec spec = launcher.compile(
                registry().resolve(List.of("PUSH", "EXPAND", "DAMAGE")));
        assertEquals(2, spec.payload().size());
        assertFalse(launcher.buildPayload(spec).isEmpty());
    }
}
