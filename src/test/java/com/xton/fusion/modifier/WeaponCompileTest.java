package com.xton.fusion.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.impl.AmplifyModifier;
import com.xton.fusion.modifier.impl.ChainModifier;
import com.xton.fusion.modifier.impl.DamageModifier;
import com.xton.fusion.modifier.impl.DepositModifier;
import com.xton.fusion.modifier.impl.ExpandModifier;
import com.xton.fusion.modifier.impl.FireModifier;
import com.xton.fusion.modifier.impl.IceModifier;
import com.xton.fusion.modifier.impl.InvertModifier;
import com.xton.fusion.modifier.impl.LifetimeModifier;
import com.xton.fusion.modifier.impl.MiningModifier;
import com.xton.fusion.modifier.impl.MultishotModifier;
import com.xton.fusion.modifier.impl.PersistModifier;
import com.xton.fusion.modifier.impl.PierceModifier;
import com.xton.fusion.modifier.impl.PushModifier;
import com.xton.fusion.modifier.impl.SpawnModifier;
import com.xton.fusion.modifier.impl.SpreadModifier;
import com.xton.fusion.modifier.impl.TeleportModifier;
import com.xton.fusion.modifier.impl.TrailModifier;

/**
 * The stack compiles into a {@link ProjectileSpec} purely (no server), so the
 * RPN emitter/transform model is fully unit-testable: emitters add bursts,
 * transforms bind to the nearest preceding emitter, and a transform with nothing
 * before it is inert.
 */
class WeaponCompileTest {

    private static final WeaponBuilder.Defaults DEFAULTS = new WeaponBuilder.Defaults(
            1.6, 30, 3.0, 2.0, 1.0, 2.5, 4.0, 1.5, 1.5, 1.5);

    private ModifierRegistry registry() {
        return new ModifierRegistry()
                .register(new PushModifier())
                .register(new DamageModifier())
                .register(new ExpandModifier(1.6))
                .register(new AmplifyModifier(1.6))
                .register(new ChainModifier(2))
                .register(new InvertModifier())
                .register(new PersistModifier(60))
                .register(new MultishotModifier(2))
                .register(new SpreadModifier(12.0))
                .register(new PierceModifier())
                .register(new LifetimeModifier(12.0))
                .register(new MiningModifier(1.0))
                .register(new FireModifier())
                .register(new IceModifier())
                .register(new DepositModifier())
                .register(new SpawnModifier())
                .register(new TrailModifier())
                .register(new TeleportModifier());
    }

    private ProjectileSpec compile(String... ids) {
        return new WeaponBuilder(DEFAULTS).compile(registry().resolve(List.of(ids)));
    }

    @Test
    void bareShotHasBaseFlightAndNoPayload() {
        ProjectileSpec p = compile();
        assertEquals(1, p.count());
        assertEquals(1.6, p.speed(), 1.0e-9);
        assertEquals(30, p.lifetimeTicks());
        assertTrue(p.payload().isEmpty(), "no emitter, no burst");
    }

    @Test
    void emitterAddsBurstFromDefaults() {
        ProjectileSpec p = compile("PUSH");
        assertEquals(1, p.payload().size());
        AoeSpec push = p.topAoe();
        assertEquals(AoeKind.PUSH, push.kind());
        assertEquals(2.0, push.radius(), 1.0e-9);
        assertEquals(1.0, push.power(), 1.0e-9);
    }

    @Test
    void expandMultipliesRadiusOfPreviousBurst() {
        AoeSpec push = compile("PUSH", "EXPAND", "EXPAND").topAoe();
        assertEquals(2.0 * 1.6 * 1.6, push.radius(), 1.0e-9);
    }

    @Test
    void amplifyMultipliesPowerOfPreviousBurst() {
        AoeSpec dmg = compile("DAMAGE", "AMPLIFY").topAoe();
        assertEquals(4.0 * 1.6, dmg.power(), 1.0e-9);
    }

    @Test
    void transformBindsToNearestPrecedingEmitterOnly() {
        // PUSH, PUSH, EXPAND → only the second push is widened.
        ProjectileSpec p = compile("PUSH", "PUSH", "EXPAND");
        assertEquals(2.0, p.payload().get(0).radius(), 1.0e-9);
        assertEquals(2.0 * 1.6, p.payload().get(1).radius(), 1.0e-9);
    }

    @Test
    void rpnComposesTwoDistinctBursts() {
        // PUSH EXPAND  DAMAGE AMPLIFY → a widened push and a stronger damage.
        ProjectileSpec p = compile("PUSH", "EXPAND", "DAMAGE", "AMPLIFY");
        assertEquals(2, p.payload().size());
        assertEquals(AoeKind.PUSH, p.payload().get(0).kind());
        assertEquals(2.0 * 1.6, p.payload().get(0).radius(), 1.0e-9);
        assertEquals(AoeKind.DAMAGE, p.payload().get(1).kind());
        assertEquals(4.0 * 1.6, p.payload().get(1).power(), 1.0e-9);
    }

    @Test
    void transformsAreInertWithoutAnEmitter() {
        ProjectileSpec p = compile("EXPAND", "AMPLIFY", "CHAIN", "INVERT", "PERSIST");
        assertTrue(p.payload().isEmpty(), "nothing to modify → no bursts appear");
        assertNull(p.topAoe());
    }

    @Test
    void invertTogglesAndTwoCancel() {
        assertTrue(compile("PUSH", "INVERT").topAoe().inverted());
        assertFalse(compile("PUSH", "INVERT", "INVERT").topAoe().inverted());
    }

    @Test
    void chainAndPersistAccumulateOnPreviousBurst() {
        AoeSpec push = compile("PUSH", "CHAIN", "CHAIN", "PERSIST").topAoe();
        assertEquals(4, push.chainCount());
        assertEquals(60, push.persistTicks());
    }

    @Test
    void flightTransformsShapeTheProjectile() {
        ProjectileSpec p = compile("MULTISHOT", "MULTISHOT", "SPREAD", "PIERCE", "LIFETIME");
        assertEquals(5, p.count());               // 1 + 2 + 2
        assertEquals(12.0, p.spreadDegrees(), 1.0e-9);
        assertTrue(p.isPierce());
        assertEquals(38, p.lifetimeTicks());      // 30 base + round(12 / 1.6 speed) = 8
        assertTrue(p.payload().isEmpty(), "flight-only weapon delivers no burst");
    }

    @Test
    void lifetimeAddsFixedDistanceRegardlessOfSpeed() {
        // The same LIFETIME on a slow shot and a fast one adds ~the same range,
        // so raising a weapon's speed doesn't inflate its tunnel length.
        ProjectileSpec slow = new WeaponBuilder(DEFAULTS)
                .compile(registry().resolve(List.of("LIFETIME")));
        WeaponBuilder fastBuilder = new WeaponBuilder(DEFAULTS);
        fastBuilder.projectile().setSpeed(4.0);
        ProjectileSpec fast = fastBuilder.compile(registry().resolve(List.of("LIFETIME")));

        double slowAddedRange = (slow.lifetimeTicks() - 30) * 1.6;
        double fastAddedRange = (fast.lifetimeTicks() - 30) * 4.0;
        assertEquals(slowAddedRange, fastAddedRange, 4.0); // ~12 blocks either way, modulo rounding
    }

    @Test
    void miningIsAnEmitterThatDoesNotPierce() {
        ProjectileSpec p = compile("MINING");
        assertTrue(p.isMining());
        assertFalse(p.isPierce(), "MINING no longer pierces on its own — add PIERCE to bore through");
        assertNotNull(p.miningAoe());
        assertEquals(1.0, p.miningAoe().radius(), 1.0e-9, "base tunnel radius 1");
        assertEquals(1, p.payload().size(), "the payload holds the MINING element");
    }

    @Test
    void expandWidensTheMiningTunnel() {
        ProjectileSpec p = compile("MINING", "EXPAND", "EXPAND");
        assertEquals(1.0 * 1.6 * 1.6, p.miningAoe().radius(), 1.0e-9);
    }

    @Test
    void miningPlusPushCarriesBothEmitters() {
        ProjectileSpec p = compile("PUSH", "MINING");
        assertTrue(p.isMining());
        assertEquals(2, p.payload().size(), "PUSH burst + MINING element");
    }

    @Test
    void fireAndIceAreEnvironmentalEmitters() {
        ProjectileSpec fire = compile("FIRE");
        assertEquals(1, fire.payload().size());
        assertEquals(AoeKind.FIRE, fire.topAoe().kind());
        assertTrue(fire.hasEnvironmental());
        assertEquals(1.5, fire.topAoe().radius(), 1.0e-9, "base fire radius");

        ProjectileSpec ice = compile("ICE", "EXPAND");
        assertEquals(AoeKind.ICE, ice.topAoe().kind());
        assertEquals(1.5 * 1.6, ice.topAoe().radius(), 1.0e-9, "EXPAND widens the freeze");
    }

    @Test
    void trailAndTeleportAreFlightFlags() {
        assertFalse(compile("FIRE").isTrail());
        assertTrue(compile("FIRE", "TRAIL").isTrail());
        assertFalse(compile("DAMAGE").isTeleport());
        assertTrue(compile("DAMAGE", "PIERCE", "TELEPORT").isTeleport());
    }

    @Test
    void spawnPushesAFreshChildThatSubsequentModifiersBuild() {
        // DAMAGE builds the root; after SPAWN, FIRE builds the child. So the root
        // carries the DAMAGE burst and one child; the child carries FIRE and nothing
        // is inherited from the parent.
        ProjectileSpec root = compile("DAMAGE", "SPAWN", "FIRE", "PIERCE");
        assertEquals(1, root.payload().size());
        assertEquals(AoeKind.DAMAGE, root.topAoe().kind());
        assertFalse(root.isPierce(), "PIERCE after SPAWN targets the child, not the root");
        assertEquals(1, root.spawns().size());

        ProjectileSpec child = root.spawns().get(0);
        assertEquals(1, child.payload().size());
        assertEquals(AoeKind.FIRE, child.topAoe().kind());
        assertTrue(child.isPierce());
        assertTrue(child.spawns().isEmpty());
    }
}
