package com.xton.fusion.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;

import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ProjectileSpec;
import com.xton.fusion.projectile.AoeBurst;
import com.xton.fusion.projectile.FusionProjectile;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.util.Scheduler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * In-process functional self-test (invoked by {@code /fusion test}, op/console
 * only). It exercises the <em>real</em> modifier compiler, projectile, and burst
 * code — the gameplay layer MockBukkit can't reach — so the mechanics a human
 * UAT would check can be verified headlessly, including from the CI smoke boot.
 *
 * <p>Two kinds of check:
 * <ul>
 *   <li><b>Compile checks</b> (pure, synchronous): compile a modifier stack and
 *       assert on the resulting {@link ProjectileSpec}/{@link AoeSpec}. These pin
 *       down the emitter/transform RPN semantics — nearest-previous binding,
 *       stacking, inert transforms — that are hard to eyeball in-world. No
 *       entities, no timing, no flake.</li>
 *   <li><b>Runtime checks</b> (live, tick-phased): fire real bursts/projectiles
 *       against dummies and blocks and assert the world changed. Freshly spawned
 *       entities are not returned by {@code getNearbyEntities} until a later
 *       tick, so we spawn on tick 0, act a few ticks later, and assert after the
 *       async projectiles have flown.</li>
 * </ul>
 *
 * <p>Each result is logged with the sentinel {@code [fusion-selftest] RESULT:
 * PASS|FAIL} that the smoke test greps for. Never runs on its own — it only
 * mutates the world when explicitly invoked, so normal servers are unaffected.
 */
public final class SelfTest {

    private static final String TAG = "[fusion-selftest]";
    /** Ticks to let a spawned entity / placed block register before we act. */
    private static final long SETTLE = 3;
    /** Ticks to let the mining ray / piercing bolt finish flying before we assert. */
    private static final long MINING_WAIT = 30;
    /** Ticks to let the PERSIST field finish pulsing before the final assertions. */
    private static final long FINAL_WAIT = 75;
    /** Reusable +X aim for the self-test's straight-line bolts. */
    private static final Vector PLUS_X = new Vector(1, 0, 0);
    /** Floating-point slop for compile-value comparisons. */
    private static final double EPS = 1.0e-6;

    private final Scheduler scheduler;
    private final ModifierRegistry registry;
    private final ProjectileLauncher launcher;
    private final AoeBurst burst;
    private final Logger log;

    public SelfTest(Scheduler scheduler, ModifierRegistry registry,
                    ProjectileLauncher launcher, AoeBurst burst, Logger log) {
        this.scheduler = scheduler;
        this.registry = registry;
        this.launcher = launcher;
        this.burst = burst;
        this.log = log;
    }

    private record Result(String name, boolean pass, String detail) {
    }

    public void run(CommandSender sender) {
        World world = sender instanceof Player p ? p.getWorld()
                : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
        if (world == null) {
            log.warning(TAG + " RESULT: FAIL (0/0) — no world available");
            sender.sendMessage(Component.text("Self-test: no world available.", NamedTextColor.RED));
            return;
        }
        Location base = (sender instanceof Player p ? p.getLocation() : world.getSpawnLocation()).clone();
        sender.sendMessage(Component.text("Running fusion self-test — results in the server log ("
                + TAG + ").", NamedTextColor.YELLOW));

        // A headless CI server has no player, and modern Paper does not keep
        // spawn chunks loaded by default — so the working chunks would unload,
        // invalidating our dummies and stopping the projectile (its inBounds
        // check trips). Pin a 3x3 around the work area for the test's duration.
        final int ccx = base.getBlockX() >> 4;
        final int ccz = base.getBlockZ() >> 4;
        forceLoad(world, ccx, ccz, true);

        List<Result> results = new ArrayList<>();
        List<Zombie> spawned = new ArrayList<>();

        // --- pure compile checks: no world, no timing; record immediately ---
        results.addAll(compileChecks());

        // --- tick 0: spawn dummies and lay the corridors ---
        Zombie pushMob = spawnDummy(world, base.clone().add(3, 0, 0), spawned);
        Zombie dmgMob = spawnDummy(world, base.clone().add(3, 0, 4), spawned);
        Zombie invertMob = spawnDummy(world, base.clone().add(3, 0, 8), spawned);
        Zombie chainNear = spawnDummy(world, base.clone().add(3, 0, 12), spawned);
        Zombie chainFar = spawnDummy(world, base.clone().add(3, 0, 15), spawned);
        // Two dummies in a line along +X, on a cleared row, for DAMAGE PIERCE.
        Zombie pierceA = spawnDummy(world, base.clone().add(2, 0, -4), spawned);
        Zombie pierceB = spawnDummy(world, base.clone().add(5, 0, -4), spawned);
        final double pierceA0 = health(pierceA);
        final double pierceB0 = health(pierceB);
        // An isolated dummy for a lingering DAMAGE PERSIST field.
        // The PERSIST dummy takes several un-mitigated DAMAGE pulses (i-frames are
        // off so each one lands), so a vanilla 20-HP zombie can die before the
        // final assertion — leaving "no mob". Give it a big health pool so it
        // survives the whole field and we can measure the cumulative damage.
        Zombie persistMob = spawnDummy(world, base.clone().add(3, 0, -10), spawned);
        toughen(persistMob, 200.0);
        final double persist0 = health(persistMob);
        // A dummy for DELAY: a DAMAGE DELAY DAMAGE bolt should hit it, then its
        // delayed charge re-detonates in place — so it takes more than one burst.
        Zombie delayMob = spawnDummy(world, base.clone().add(3, 0, -30), spawned);
        final double delayMob0 = health(delayMob);
        // A mob off the firing line: a HOMING bolt must curve sideways into it.
        Zombie homingMob = spawnDummy(world, base.clone().add(5, 0, -32), spawned);
        final double homingMob0 = health(homingMob);

        int bx = base.getBlockX();
        int by = base.getBlockY() + 1;
        int bz = base.getBlockZ();
        final int firstDirt = 3;
        final int lastDirt = 7;
        boolean corridorOk = layCorridor(world, bx, by, bz, firstDirt, lastDirt, results);
        // A second corridor with an obsidian plug: dirt, then obsidian, then dirt.
        boolean hardOk = layHardCorridor(world, bx, by, bz - 2);
        // A cleared row for the piercing bolt to travel unobstructed to the mobs.
        clearRow(world, bx, by, bz - 4, 8);
        // A two-block run: bare MINING should break the first and stop.
        boolean aloneOk = layTwoBlockRun(world, bx, by, bz - 6);
        // A plus of dirt (centre + above + beside) at dx 5: EXPAND should widen
        // the bore enough to break the off-axis blocks.
        final int plusCol = 5;
        boolean plusOk = layPlusColumn(world, bx, by, bz - 8, plusCol);
        // A grass plant (non-solid) on the flight line with ground beneath: a
        // mining ray should carve through the plant AND the ground it hides.
        boolean vegOk = layVegetation(world, bx, by, bz - 12, plusCol);
        // Environmental emitters: snow to melt (FIRE), water to freeze (ICE), an
        // air pocket ending in a wall to backfill (DEPOSIT), and a clear corridor
        // to fill along its wake (DEPOSIT + TRAIL). A mob on the FIRE line to ignite.
        final int envCol = 3;
        boolean fireOk = laySnow(world, bx, by, bz - 14, envCol);
        Zombie fireMob = spawnDummy(world, base.clone().add(5, 1, -14), spawned);
        boolean iceOk = layWater(world, bx, by, bz - 16, envCol);
        boolean depositOk = layStopBlock(world, bx, by, bz - 18, 5);
        boolean trailOk = clearRowReturn(world, bx, by, bz - 20, 8);
        // A wall ahead with a mob behind the firing point: a BOUNCE bolt must
        // rebound off the wall and hit the mob on the way back, and a SPAWN bolt's
        // child must reflect off the wall to reach it (not crash into the wall).
        boolean bounceOk = layBounceRange(world, bx, by, bz - 22);
        Zombie bounceMob = spawnDummy(world, base.clone().add(-3, 1, -22), spawned);
        final double bounceMob0 = health(bounceMob);
        boolean spawnReflOk = layBounceRange(world, bx, by, bz - 24);
        Zombie spawnReflMob = spawnDummy(world, base.clone().add(-3, 1, -24), spawned);
        final double spawnReflMob0 = health(spawnReflMob);
        // A clear corridor for a DEPOSIT:DIRT PIERCE TRAIL bolt: the trail warm-up
        // should leave the caster's own tile empty and only fill downrange.
        boolean trailWarmupOk = clearRowReturn(world, bx, by, bz - 26, 8);
        clearRow(world, bx, by, bz - 30, 5); // clear path to the DELAY dummy at dx3
        for (int dz = 0; dz <= 3; dz++) {   // a small arena for the HOMING bolt to curve in
            clearRow(world, bx, by, bz - 34 + dz, 6);
        }
        // An obsidian-plugged corridor: a deep MINING stack should chew through it.
        boolean heavyMineOk = layHardCorridor(world, bx, by, bz - 36);
        // A hurt COW floating in open air (passive, so HEAL mends it — hostiles are
        // skipped). Placed up high like the MOB test's cow, which spawns valid there.
        final Cow healCow = spawnCow(world, new Location(world, bx + 2.5, by + 3, bz + 0.5));
        if (healCow != null) {
            healCow.setHealth(4.0);
        }
        final double healCow0 = healCow != null ? healCow.getHealth() : 0;

        final double gravityLaunchY = by + 20;
        final FusionProjectile[] gravityBolt = new FusionProjectile[1];

        // --- tick SETTLE: dummies + blocks are registered now; act on them ---
        final boolean fireMining = corridorOk;
        final boolean fireHard = hardOk;
        final boolean fireAlone = aloneOk;
        final boolean firePlus = plusOk;
        final boolean fireVeg = vegOk;
        final boolean fireEnv = fireOk;
        final boolean iceEnv = iceOk;
        final boolean depositEnv = depositOk;
        final boolean trailEnv = trailOk;
        final boolean fireBounce = bounceOk;
        final boolean fireSpawnRefl = spawnReflOk;
        final boolean fireTrailWarmup = trailWarmupOk;
        final boolean fireHeavyMine = heavyMineOk;
        scheduler.runLater(() -> {
            results.add(pushKnockback(world, pushMob));
            results.add(damageHurts(world, dmgMob));
            results.add(invertPullsInward(world, invertMob));
            results.add(chainHopsToSecond(world, chainNear, chainFar));
            if (persistMob != null && persistMob.isValid()) {
                burst.fire(world, persistMob.getLocation(), firstAoe("DAMAGE", "PERSIST"), null);
            }
            if (healCow != null && healCow.isValid()) {
                burst.fire(world, healCow.getLocation(), firstAoe("HEAL"), null);
            }
            if (delayMob != null && delayMob.isValid()) {
                fireBolt(world, at(world, bx, by, bz - 30), PLUS_X, false, "DAMAGE", "DELAY:0.5", "DAMAGE");
            }
            // MOB:COW launches a real cow entity as the projectile; verify one appears.
            Entity cowShot = launcher.spawnMobShot(world,
                    new Location(world, bx + 0.5, by + 3, bz + 0.5), PLUS_X.clone().multiply(0.4), compile("MOB:COW"));
            results.add(new Result("mob-launches-entity", cowShot instanceof Cow && cowShot.isValid(),
                    "spawned=" + (cowShot == null ? "null" : cowShot.getType())));
            if (cowShot != null) {
                cowShot.remove();
            }
            if (homingMob != null && homingMob.isValid()) {
                // Fire +X from 2 blocks to the mob's side; only homing can curve into it.
                fireBolt(world, at(world, bx, by, bz - 34), PLUS_X, false, "DAMAGE", "HOMING", "HOMING");
            }
            if (fireMining) {
                fireBolt(world, at(world, bx, by, bz), PLUS_X, false, "MINING", "PIERCE");
            }
            if (fireHard) {
                fireBolt(world, at(world, bx, by, bz - 2), PLUS_X, false, "MINING", "PIERCE");
            }
            fireBolt(world, at(world, bx, by, bz - 4), PLUS_X, false, "DAMAGE", "PIERCE");
            if (fireAlone) {
                fireBolt(world, at(world, bx, by, bz - 6), PLUS_X, false, "MINING");
            }
            if (firePlus) {
                fireBolt(world, at(world, bx, by, bz - 8), PLUS_X, false, "MINING", "PIERCE", "EXPAND");
            }
            if (fireVeg) {
                fireBolt(world, at(world, bx, by, bz - 12), PLUS_X, false, "MINING", "PIERCE", "EXPAND");
            }
            if (fireEnv) {
                fireBolt(world, at(world, bx, by, bz - 14), PLUS_X, false, "FIRE", "PIERCE");
            }
            if (iceEnv) {
                fireBolt(world, at(world, bx, by, bz - 16), PLUS_X, false, "ICE", "PIERCE");
            }
            if (depositEnv) {
                fireBolt(world, at(world, bx, by, bz - 18), PLUS_X, false, "DEPOSIT:DIRT");
            }
            if (trailEnv) {
                fireBolt(world, at(world, bx, by, bz - 20), PLUS_X, false, "DEPOSIT:DIRT", "PIERCE", "TRAIL");
            }
            if (fireBounce) {
                fireBolt(world, at(world, bx, by, bz - 22), PLUS_X, false, "DAMAGE", "BOUNCE");
            }
            if (fireSpawnRefl) {
                fireBolt(world, at(world, bx, by, bz - 24), PLUS_X, false, "DAMAGE", "SPAWN", "DAMAGE");
            }
            if (fireTrailWarmup) {
                fireBolt(world, at(world, bx, by, bz - 26), PLUS_X, false, "DEPOSIT:DIRT", "PIERCE", "TRAIL");
            }
            if (fireHeavyMine) { // five MININGs stack past obsidian's hardness
                fireBolt(world, at(world, bx, by, bz - 36), PLUS_X, false,
                        "MINING", "MINING", "MINING", "MINING", "MINING", "PIERCE");
            }
            gravityBolt[0] = fireBolt(world,
                    new Location(world, bx + 0.5, gravityLaunchY, bz + 0.5), PLUS_X, true);
        }, SETTLE);

        // Shortly after firing: the piercing DAMAGE bolt has hit both dummies.
        scheduler.runLater(() -> {
            results.add(pierceDamagesBoth(pierceA, pierceB, pierceA0, pierceB0));
            results.add(healRestoresHealth(healCow, healCow0));
        }, SETTLE + 8);

        // --- after the bolts have flown: assert blocks ---
        scheduler.runLater(() -> {
            if (fireMining) {
                results.add(miningResult(world, bx, by, bz, firstDirt, lastDirt));
            }
            if (fireHard) {
                results.add(miningStopsAtHardBlock(world, bx, by, bz - 2));
            }
            if (fireAlone) {
                results.add(miningAloneStops(world, bx, by, bz - 6));
            }
            if (firePlus) {
                results.add(expandWidensTunnel(world, bx, by, bz - 8, plusCol));
            }
            if (fireVeg) {
                results.add(miningClearsVegetation(world, bx, by, bz - 12, plusCol));
            }
            if (fireEnv) {
                results.add(fireMeltsSnow(world, bx, by, bz - 14, envCol));
                results.add(fireIgnitesMob(fireMob));
            }
            if (iceEnv) {
                results.add(iceFreezesWater(world, bx, by, bz - 16, envCol));
            }
            if (depositEnv) {
                results.add(depositFillsAir(world, bx, by, bz - 18, 4));
            }
            if (trailEnv) {
                results.add(trailFillsPath(world, bx, by, bz - 20, envCol));
            }
            if (fireBounce) {
                results.add(damagedOnRebound("bounce-reflects-off-wall", bounceMob, bounceMob0));
            }
            if (fireSpawnRefl) {
                results.add(damagedOnRebound("spawn-reflects-off-wall", spawnReflMob, spawnReflMob0));
            }
            if (fireTrailWarmup) {
                results.add(trailWarmupSparesCaster(world, bx, by, bz - 26));
            }
            if (fireHeavyMine) {
                results.add(heavyMiningBreaksObsidian(world, bx, by, bz - 36));
            }
            results.add(delayReDetonates(delayMob, delayMob0));
            results.add(homingCurvesIntoTarget(homingMob, homingMob0));
        }, SETTLE + MINING_WAIT);

        // --- last: the persist field has finished pulsing and the gravity bolt
        // has expired; assert those, then finalize ---
        scheduler.runLater(() -> {
            results.add(persistPulsesMultipleTimes(persistMob, persist0));
            results.add(gravityFalls(gravityBolt[0], gravityLaunchY));
            finish(results, spawned, sender);
            forceLoad(world, ccx, ccz, false); // release the chunk tickets
        }, SETTLE + FINAL_WAIT);
    }

    // ===== compile checks (pure) =====

    /**
     * Assert the emitter/transform RPN compiler produces the right spec. Pure and
     * deterministic — these are the crown jewels: they pin the model semantics
     * (nearest-previous binding, stacking, inert transforms, flight flags) that a
     * human can barely see in-world.
     */
    private List<Result> compileChecks() {
        List<Result> r = new ArrayList<>();

        // Burst is opt-in: PUSH/DAMAGE deliver a terminus burst; a mining ray
        // (carved along the flight) and transform-only stacks deliver none.
        boolean miningNoBurst = launcher.buildPayload(compile("MINING")).isEmpty();
        boolean pushBursts = !launcher.buildPayload(compile("PUSH")).isEmpty();
        boolean transformOnlyEmpty = launcher.buildPayload(compile("EXPAND")).isEmpty()
                && launcher.buildPayload(compile("AMPLIFY")).isEmpty()
                && launcher.buildPayload(compile("INVERT")).isEmpty();
        r.add(new Result("compile:payload-opt-in", miningNoBurst && pushBursts && transformOnlyEmpty,
                "mining.noBurst=" + miningNoBurst + " push.bursts=" + pushBursts
                        + " transformOnly.empty=" + transformOnlyEmpty));

        // EXPAND multiplies the previous burst's radius, and stacks multiplicatively.
        double r0 = firstAoe("PUSH").radius();
        double r1 = firstAoe("PUSH", "EXPAND").radius();
        double r2 = firstAoe("PUSH", "EXPAND", "EXPAND").radius();
        boolean expandOk = r1 > r0 + EPS && r2 > r1 + EPS
                && approx(r1 / r0, r2 / r1); // consistent per-apply factor
        r.add(new Result("compile:expand-scales-radius", expandOk,
                String.format("radius %.3f -> %.3f -> %.3f", r0, r1, r2)));

        // AMPLIFY multiplies the previous burst's power, and stacks.
        double p0 = firstAoe("DAMAGE").power();
        double p1 = firstAoe("DAMAGE", "AMPLIFY").power();
        double p2 = firstAoe("DAMAGE", "AMPLIFY", "AMPLIFY").power();
        boolean amplifyOk = p1 > p0 + EPS && p2 > p1 + EPS && approx(p1 / p0, p2 / p1);
        r.add(new Result("compile:amplify-scales-power", amplifyOk,
                String.format("power %.3f -> %.3f -> %.3f", p0, p1, p2)));

        // Nearest-previous binding: a transform touches ONLY the last emitter.
        // PUSH PUSH EXPAND → first push untouched, second push widened.
        ProjectileSpec twoPush = compile("PUSH", "PUSH", "EXPAND");
        boolean bindOk = false;
        String bindDetail = "payload size " + twoPush.payload().size();
        if (twoPush.payload().size() == 2) {
            double a = twoPush.payload().get(0).radius();
            double b = twoPush.payload().get(1).radius();
            bindOk = approx(a, r0) && b > a + EPS; // first == bare push, second widened
            bindDetail = String.format("push[0]=%.3f (bare=%.3f) push[1]=%.3f", a, r0, b);
        }
        r.add(new Result("compile:nearest-previous-binding", bindOk, bindDetail));

        // One shot can carry two independently-scaled bursts.
        ProjectileSpec twoEmit = compile("PUSH", "EXPAND", "DAMAGE", "AMPLIFY");
        boolean twoEmitOk = twoEmit.payload().size() == 2
                && twoEmit.payload().get(0).kind() == AoeKind.PUSH
                && twoEmit.payload().get(1).kind() == AoeKind.DAMAGE
                && twoEmit.payload().get(0).radius() > r0 + EPS
                && twoEmit.payload().get(1).power() > p0 + EPS;
        r.add(new Result("compile:two-bursts-one-shot", twoEmitOk,
                "payload=" + describe(twoEmit)));

        // MULTISHOT adds projectiles and stacks by a constant per-apply delta.
        int c0 = compile("DAMAGE").count();
        int c1 = compile("DAMAGE", "MULTISHOT").count();
        int c2 = compile("DAMAGE", "MULTISHOT", "MULTISHOT").count();
        boolean multiOk = c1 > c0 && (c2 - c1) == (c1 - c0);
        r.add(new Result("compile:multishot-adds-projectiles", multiOk,
                "count " + c0 + " -> " + c1 + " -> " + c2));

        // SPREAD widens the aim cone and stacks.
        double s0 = compile("DAMAGE").spreadDegrees();
        double s1 = compile("DAMAGE", "SPREAD").spreadDegrees();
        double s2 = compile("DAMAGE", "SPREAD", "SPREAD").spreadDegrees();
        boolean spreadOk = s0 == 0 && s1 > 0 && s2 > s1;
        r.add(new Result("compile:spread-widens-cone", spreadOk,
                String.format("spread %.1f -> %.1f -> %.1f", s0, s1, s2)));

        // LIFETIME lengthens flight (more range).
        int l0 = compile("DAMAGE").lifetimeTicks();
        int l1 = compile("DAMAGE", "LIFETIME").lifetimeTicks();
        int l2 = compile("DAMAGE", "LIFETIME", "LIFETIME").lifetimeTicks();
        boolean lifeOk = l1 > l0 && (l2 - l1) == (l1 - l0);
        r.add(new Result("compile:lifetime-extends-flight", lifeOk,
                "ticks " + l0 + " -> " + l1 + " -> " + l2));

        // INVERT toggles the PUSH direction; two INVERTs cancel.
        boolean inv1 = firstAoe("PUSH", "INVERT").inverted();
        boolean inv2 = firstAoe("PUSH", "INVERT", "INVERT").inverted();
        r.add(new Result("compile:invert-toggles", inv1 && !inv2,
                "one=" + inv1 + " two=" + inv2));

        // CHAIN and PERSIST decorate the previous burst.
        int chain = firstAoe("DAMAGE", "CHAIN").chainCount();
        int persist = firstAoe("PUSH", "PERSIST").persistTicks();
        r.add(new Result("compile:chain-and-persist-accumulate", chain > 0 && persist > 0,
                "chainCount=" + chain + " persistTicks=" + persist));

        // MINING is an emitter that does NOT pierce on its own; add PIERCE to
        // bore, and EXPAND widens the tunnel radius. PIERCE is a flight flag.
        ProjectileSpec pierce = compile("DAMAGE", "PIERCE");
        ProjectileSpec mining = compile("MINING");
        ProjectileSpec miningPierce = compile("MINING", "PIERCE");
        ProjectileSpec miningExpand = compile("MINING", "EXPAND");
        boolean flagsOk = pierce.isPierce() && !pierce.isMining()
                && mining.isMining() && !mining.isPierce()
                && miningPierce.isPierce()
                && miningExpand.miningAoe().radius() > mining.miningAoe().radius();
        // Stacking MINING raises its break-hardness (stored in the element's power)
        // without adding a duplicate bore; AMPLIFY scales the same hardness.
        double mineH1 = compile("MINING").miningAoe().power();
        double mineH2 = compile("MINING", "MINING").miningAoe().power();
        boolean mineHardOk = mineH1 > 0 && mineH2 > mineH1
                && compile("MINING", "MINING").payload().size() == 1;
        r.add(new Result("compile:mining-hardness-stacks", mineHardOk,
                "hardness " + mineH1 + " -> " + mineH2));

        r.add(new Result("compile:mining-and-pierce", flagsOk,
                "mining.pierce=" + mining.isPierce() + " miningPierce.pierce=" + miningPierce.isPierce()
                        + " mining.r=" + mining.miningAoe().radius()
                        + " miningExpand.r=" + miningExpand.miningAoe().radius()));

        // FIRE/ICE are environmental emitters; TRAIL/TELEPORT are flight flags;
        // SPAWN pushes a fresh child that every later modifier builds instead.
        ProjectileSpec fire = compile("FIRE");
        ProjectileSpec cluster = compile("DAMAGE", "SPAWN", "FIRE", "PIERCE");
        boolean structOk = fire.hasEnvironmental() && fire.payload().get(0).kind() == AoeKind.FIRE
                && compile("ICE").payload().get(0).kind() == AoeKind.ICE
                && compile("FIRE", "TRAIL").isTrail()
                && compile("DAMAGE", "TELEPORT").isTeleport()
                && cluster.spawns().size() == 1
                && !cluster.isPierce() // PIERCE after SPAWN targets the child, not the root
                && cluster.spawns().get(0).payload().get(0).kind() == AoeKind.FIRE
                && cluster.spawns().get(0).isPierce();
        r.add(new Result("compile:environmental-and-structural", structOk,
                "fire.env=" + fire.hasEnvironmental() + " cluster.children=" + cluster.spawns().size()
                        + " child.pierce=" + cluster.spawns().get(0).isPierce()));

        // DEPOSIT:<block> resolves a parameterized ID to a material-bound emitter.
        ProjectileSpec deposit = compile("DEPOSIT:DIRT");
        boolean depositOk = deposit.payload().size() == 1
                && deposit.payload().get(0).kind() == AoeKind.DEPOSIT
                && deposit.payload().get(0).material() == Material.DIRT;
        r.add(new Result("compile:deposit-parameterized", depositOk,
                "material=" + (deposit.payload().isEmpty() ? "<none>"
                        : deposit.payload().get(0).material())));

        // HEAL/PULL complements: HEAL is its own kind; PULL is a pre-inverted PUSH.
        boolean complementOk = compile("HEAL").payload().get(0).kind() == AoeKind.HEAL
                && compile("PULL").payload().get(0).kind() == AoeKind.PUSH
                && compile("PULL").payload().get(0).inverted()
                && !compile("PUSH").payload().get(0).inverted();
        r.add(new Result("compile:heal-pull-complements", complementOk,
                "healKind=" + compile("HEAL").payload().get(0).kind()
                        + " pullInverted=" + compile("PULL").payload().get(0).inverted()));

        // DELAY:n pushes a delayed, in-place child that later modifiers build.
        ProjectileSpec delayed = compile("PULL", "DELAY:1", "DAMAGE");
        boolean delayOk = delayed.spawns().size() == 1
                && delayed.spawns().get(0).spawnDelayTicks() == 20
                && delayed.spawns().get(0).payload().get(0).kind() == AoeKind.DAMAGE
                && delayed.spawns().get(0).speed() < EPS;
        r.add(new Result("compile:delay-child", delayOk,
                "delayTicks=" + (delayed.spawns().isEmpty() ? "-"
                        : delayed.spawns().get(0).spawnDelayTicks())));

        // HOMING is a stacking flight flag.
        boolean homingOk = compile("DAMAGE", "HOMING").isHoming()
                && !compile("DAMAGE").isHoming()
                && compile("DAMAGE", "HOMING", "HOMING").homing() == 2;
        r.add(new Result("compile:homing-flag", homingOk,
                "homing2=" + compile("DAMAGE", "HOMING", "HOMING").homing()));

        // MOB:<type> parses a spawnable living entity to launch as the projectile.
        ProjectileSpec cowShot = compile("MOB:COW");
        boolean mobOk = cowShot.mobType() == EntityType.COW && compile("MOB:ENDER_DRAGON").mobType() == null;
        r.add(new Result("compile:mob-parameterized", mobOk,
                "cow=" + cowShot.mobType() + " dragonBlocked=" + (compile("MOB:ENDER_DRAGON").mobType() == null)));

        // Flight tuning: GRAVITY arcs, VISIBLE/INVISIBLE toggle the trail, and the
        // parameterized SPEED:<v>/DURATION:<s> pin absolute values.
        boolean tuneOk = compile("DAMAGE", "GRAVITY").hasGravity()
                && !compile("DAMAGE").hasGravity()
                && !compile("DAMAGE", "INVISIBLE").hasVisibleTrail()
                && compile("DAMAGE", "INVISIBLE", "VISIBLE").hasVisibleTrail()
                && Math.abs(compile("SPEED:0.8").speed() - 0.8) < EPS
                && compile("DURATION:3").lifetimeTicks() == 60;
        r.add(new Result("compile:flight-tuning", tuneOk,
                "gravity=" + compile("DAMAGE", "GRAVITY").hasGravity()
                        + " speed=" + compile("SPEED:0.8").speed()
                        + " durTicks=" + compile("DURATION:3").lifetimeTicks()));

        // BOUNCE is a flight flag; a fresh SPAWN child does not inherit it.
        ProjectileSpec bounceSpawn = compile("DAMAGE", "BOUNCE", "SPAWN", "DAMAGE");
        boolean bounceOk = compile("DAMAGE", "BOUNCE").isBounce()
                && !compile("DAMAGE").isBounce()
                && bounceSpawn.isBounce()
                && !bounceSpawn.spawns().get(0).isBounce();
        r.add(new Result("compile:bounce-flag", bounceOk,
                "bounce=" + compile("DAMAGE", "BOUNCE").isBounce()
                        + " childInherits=" + bounceSpawn.spawns().get(0).isBounce()));

        return r;
    }

    // ===== runtime checks (live world) =====

    /** Add or remove plugin chunk tickets over a 3x3 area around a chunk. */
    private void forceLoad(World world, int ccx, int ccz, boolean load) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (load) {
                    world.addPluginChunkTicket(ccx + dx, ccz + dz, launcher.plugin());
                } else {
                    world.removePluginChunkTicket(ccx + dx, ccz + dz, launcher.plugin());
                }
            }
        }
    }

    /** PUSH burst imparts outward knockback to the (now-registered) dummy. */
    private Result pushKnockback(World world, Zombie mob) {
        if (mob == null || !mob.isValid()) {
            return new Result("push-knockback", false, "no mob");
        }
        // Fire offset from the mob so the shove is horizontal, and it is well
        // inside the burst radius.
        burst.fire(world, mob.getLocation().clone().add(-1.0, 0, 0), firstAoe("PUSH"), null);
        double speed = mob.getVelocity().length();
        return new Result("push-knockback", speed > 0.1, "velocity=" + String.format("%.3f", speed));
    }

    /** DAMAGE burst lowers the dummy's health. */
    private Result damageHurts(World world, Zombie mob) {
        if (mob == null || !mob.isValid()) {
            return new Result("damage-hurts", false, "no mob");
        }
        double before = mob.getHealth();
        burst.fire(world, mob.getLocation(), firstAoe("DAMAGE"), null);
        double after = mob.getHealth();
        return new Result("damage-hurts", after < before, "health " + before + " -> " + after);
    }

    /**
     * An inverted PUSH pulls the dummy toward the burst centre instead of away.
     * Fire the centre one block to the mob's +X so a normal push shoves it -X and
     * an inverted one drags it +X; assert it moved +X.
     */
    private Result invertPullsInward(World world, Zombie mob) {
        if (mob == null || !mob.isValid()) {
            return new Result("invert-pulls-inward", false, "no mob");
        }
        Location centre = mob.getLocation().clone().add(1.0, 0, 0);
        burst.fire(world, centre, firstAoe("PUSH", "INVERT"), null);
        double vx = mob.getVelocity().getX();
        return new Result("invert-pulls-inward", vx > 0.05, "vx=" + String.format("%.3f", vx));
    }

    /**
     * A CHAIN burst hops to a second entity outside the direct blast radius. The
     * far dummy sits beyond the burst radius but within chain range, so only the
     * chain can reach it — if its health drops, the hop fired.
     */
    private Result chainHopsToSecond(World world, Zombie near, Zombie far) {
        if (near == null || !near.isValid() || far == null || !far.isValid()) {
            return new Result("chain-hops-to-second", false, "missing mobs");
        }
        double farBefore = far.getHealth();
        burst.fire(world, near.getLocation(), firstAoe("DAMAGE", "CHAIN"), null);
        double farAfter = far.getHealth();
        return new Result("chain-hops-to-second", farAfter < farBefore,
                "far health " + farBefore + " -> " + farAfter);
    }

    /** Lay a known dirt run in front along +X, with a clear approach before it. */
    private boolean layCorridor(World world, int bx, int by, int bz,
                                int firstDirt, int lastDirt, List<Result> results) {
        try {
            for (int dx = 0; dx <= lastDirt + 1; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            int placed = 0;
            for (int dx = firstDirt; dx <= lastDirt; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.DIRT, false);
                if (world.getBlockAt(bx + dx, by, bz).getType() == Material.DIRT) {
                    placed++;
                }
            }
            log.info(TAG + " mining: placed " + placed + " dirt blocks in the corridor");
            return true;
        } catch (Exception e) {
            results.add(new Result("mining-breaks-blocks", false, "setup exception: " + e));
            return false;
        }
    }

    /**
     * Lay a corridor with an obsidian plug: dirt at dx 3-4, obsidian at dx 5,
     * dirt at dx 6-7. A mining ray should carve the leading dirt, stop at the
     * obsidian (harder than its cap), and leave everything past it intact.
     */
    private boolean layHardCorridor(World world, int bx, int by, int bz) {
        try {
            for (int dx = 0; dx <= 8; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            world.getBlockAt(bx + 3, by, bz).setType(Material.DIRT, false);
            world.getBlockAt(bx + 4, by, bz).setType(Material.DIRT, false);
            world.getBlockAt(bx + 5, by, bz).setType(Material.OBSIDIAN, false);
            world.getBlockAt(bx + 6, by, bz).setType(Material.DIRT, false);
            world.getBlockAt(bx + 7, by, bz).setType(Material.DIRT, false);
            return world.getBlockAt(bx + 5, by, bz).getType() == Material.OBSIDIAN;
        } catch (Exception e) {
            log.warning(TAG + " hard-corridor setup failed: " + e);
            return false;
        }
    }

    /** Clear a straight air corridor along +X for a projectile to travel. */
    private void clearRow(World world, int bx, int by, int bz, int length) {
        for (int dx = 0; dx <= length; dx++) {
            world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
        }
    }

    /** The block-centre location for row/col {@code (bx,by,bz)}. */
    private Location at(World world, int bx, int by, int bz) {
        return new Location(world, bx + 0.5, by + 0.5, bz + 0.5);
    }

    /** Compile {@code ids}, seed gravity, and launch a bolt; returns it so tests can observe it. */
    private FusionProjectile fireBolt(World world, Location origin, Vector dir, boolean gravity, String... ids) {
        ProjectileSpec spec = compile(ids);
        spec.setGravity(gravity);
        Vector velocity = dir.clone().normalize().multiply(spec.speed());
        return launcher.fireDirect(world, origin, velocity, spec);
    }

    /** A two-block dirt run at dx 3-4; bare MINING should break only the first. */
    private boolean layTwoBlockRun(World world, int bx, int by, int bz) {
        try {
            for (int dx = 0; dx <= 8; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            world.getBlockAt(bx + 3, by, bz).setType(Material.DIRT, false);
            world.getBlockAt(bx + 4, by, bz).setType(Material.DIRT, false);
            return world.getBlockAt(bx + 3, by, bz).getType() == Material.DIRT
                    && world.getBlockAt(bx + 4, by, bz).getType() == Material.DIRT;
        } catch (Exception e) {
            log.warning(TAG + " two-block-run setup failed: " + e);
            return false;
        }
    }

    /**
     * A plus of dirt at column {@code col}: the centre (on the flight line), one
     * above, and one beside. A radius-1 bore breaks only the centre; an
     * EXPAND-widened bore reaches the off-axis blocks.
     */
    private boolean layPlusColumn(World world, int bx, int by, int bz, int col) {
        try {
            for (int dx = 0; dx <= 8; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            world.getBlockAt(bx + col, by, bz).setType(Material.DIRT, false);      // centre
            world.getBlockAt(bx + col, by + 1, bz).setType(Material.DIRT, false);  // above
            world.getBlockAt(bx + col, by, bz + 1).setType(Material.DIRT, false);  // beside
            return world.getBlockAt(bx + col, by + 1, bz).getType() == Material.DIRT
                    && world.getBlockAt(bx + col, by, bz + 1).getType() == Material.DIRT;
        } catch (Exception e) {
            log.warning(TAG + " plus-column setup failed: " + e);
            return false;
        }
    }

    private double health(Zombie mob) {
        return mob == null ? 0 : mob.getHealth();
    }

    /** Spawn a passive, inert cow (a valid HEAL target, unlike the hostile zombies). */
    private Cow spawnCow(World world, Location loc) {
        try {
            return world.spawn(loc, Cow.class, c -> {
                c.setAI(false);
                c.setGravity(false);
                c.setSilent(true);
                c.setRemoveWhenFarAway(false);
            });
        } catch (Exception e) {
            log.warning(TAG + " could not spawn cow: " + e);
            return null;
        }
    }

    /** A HOMING bolt fired past the mob curves sideways into it, so its health drops. */
    private Result homingCurvesIntoTarget(Zombie mob, double before) {
        if (mob == null || !mob.isValid()) {
            return new Result("homing-curves-into-target", false, "no mob");
        }
        boolean ok = mob.getHealth() < before;
        return new Result("homing-curves-into-target", ok,
                String.format("health %.1f -> %.1f", before, mob.getHealth()));
    }

    /** A deep MINING stack (hardness past 50) bores through the obsidian plug the base ray stops at. */
    private Result heavyMiningBreaksObsidian(World world, int bx, int by, int bz) {
        Material m = world.getBlockAt(bx + 5, by, bz).getType();
        return new Result("mining-stacks-break-obsidian", m != Material.OBSIDIAN, "obsidian=" + m);
    }

    /** A DAMAGE DELAY DAMAGE bolt hits, then its delayed charge re-detonates — more than one burst lands. */
    private Result delayReDetonates(Zombie mob, double before) {
        if (mob == null || !mob.isValid()) {
            return new Result("delay-re-detonates", false, "no mob");
        }
        double single = firstAoe("DAMAGE").power();
        double taken = before - mob.getHealth();
        return new Result("delay-re-detonates", taken > single + EPS,
                String.format("took %.1f (single burst %.1f)", taken, single));
    }

    /** A HEAL burst mends the hurt (passive) cow — its health climbs back up. */
    private Result healRestoresHealth(Cow mob, double before) {
        if (mob == null || !mob.isValid()) {
            return new Result("heal-restores-health", false, "no mob");
        }
        double after = mob.getHealth();
        Result res = new Result("heal-restores-health", after > before,
                String.format("health %.1f -> %.1f", before, after));
        mob.remove();
        return res;
    }

    /** Raise a dummy's max health and fill it, so repeated pulses can't kill it mid-test. */
    private void toughen(Zombie mob, double maxHealth) {
        if (mob == null) {
            return;
        }
        AttributeInstance attr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHealth);
            mob.setHealth(maxHealth);
        }
    }

    /** A grass plant (non-solid) on the flight line at {@code col}, with ground beneath. */
    private boolean layVegetation(World world, int bx, int by, int bz, int col) {
        try {
            for (int dx = 0; dx <= 8; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            world.getBlockAt(bx + col, by - 1, bz).setType(Material.DIRT, false);        // ground it hides
            world.getBlockAt(bx + col, by, bz).setType(Material.SHORT_GRASS, false);      // plant on the flight line
            return world.getBlockAt(bx + col, by, bz).getType() == Material.SHORT_GRASS;
        } catch (Exception e) {
            log.warning(TAG + " vegetation setup failed: " + e);
            return false;
        }
    }

    /** Clear a row and place a SNOW_BLOCK on the flight line at {@code col} (for FIRE). */
    private boolean laySnow(World world, int bx, int by, int bz, int col) {
        try {
            for (int dx = 0; dx <= 8; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            world.getBlockAt(bx + col, by, bz).setType(Material.SNOW_BLOCK, false);
            return world.getBlockAt(bx + col, by, bz).getType() == Material.SNOW_BLOCK;
        } catch (Exception e) {
            log.warning(TAG + " snow setup failed: " + e);
            return false;
        }
    }

    /** Clear a row and place a WATER source on the flight line at {@code col} (for ICE). */
    private boolean layWater(World world, int bx, int by, int bz, int col) {
        try {
            for (int dx = 0; dx <= 8; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            world.getBlockAt(bx + col, by, bz).setType(Material.WATER, false);
            return world.getBlockAt(bx + col, by, bz).getType() == Material.WATER;
        } catch (Exception e) {
            log.warning(TAG + " water setup failed: " + e);
            return false;
        }
    }

    /** Clear a row and cap it with a solid stone wall at {@code col} (a DEPOSIT terminus). */
    private boolean layStopBlock(World world, int bx, int by, int bz, int col) {
        try {
            for (int dx = 0; dx <= 8; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            world.getBlockAt(bx + col, by, bz).setType(Material.STONE, false);
            return world.getBlockAt(bx + col, by, bz).getType() == Material.STONE;
        } catch (Exception e) {
            log.warning(TAG + " stop-block setup failed: " + e);
            return false;
        }
    }

    /** Clear a straight air corridor and report success (for the TRAIL fill test). */
    private boolean clearRowReturn(World world, int bx, int by, int bz, int length) {
        try {
            clearRow(world, bx, by, bz, length);
            return world.getBlockAt(bx + 1, by, bz).getType().isAir();
        } catch (Exception e) {
            log.warning(TAG + " trail-corridor setup failed: " + e);
            return false;
        }
    }

    /** Clear a run that straddles the origin (dx -5..+6) and cap it with a wall at dx +5. */
    private boolean layBounceRange(World world, int bx, int by, int bz) {
        try {
            for (int dx = -5; dx <= 6; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            world.getBlockAt(bx + 5, by, bz).setType(Material.STONE, false);
            return world.getBlockAt(bx + 5, by, bz).getType() == Material.STONE;
        } catch (Exception e) {
            log.warning(TAG + " bounce-range setup failed: " + e);
            return false;
        }
    }

    /**
     * A DEPOSIT:DIRT PIERCE TRAIL bolt's warm-up leaves the caster's own tile empty
     * (no self-flood) while still filling downrange past the warm-up distance.
     */
    private Result trailWarmupSparesCaster(World world, int bx, int by, int bz) {
        Material atCaster = world.getBlockAt(bx, by, bz).getType();      // origin tile — must stay clear
        Material downrange = world.getBlockAt(bx + 5, by, bz).getType(); // past warm-up — must be filled
        boolean ok = atCaster.isAir() && downrange == Material.DIRT;
        return new Result("trail-warmup-spares-caster", ok,
                "caster=" + atCaster + " downrange=" + downrange);
    }

    /** A mob behind the firing point loses health, so the shot (or its child) rebounded off the wall to reach it. */
    private Result damagedOnRebound(String name, Zombie mob, double before) {
        if (mob == null || !mob.isValid()) {
            return new Result(name, false, "no mob");
        }
        boolean ok = mob.getHealth() < before;
        return new Result(name, ok,
                String.format("health %.1f -> %.1f (hit on the rebound)", before, mob.getHealth()));
    }

    /** FIRE melts the snow on its line — the block is no longer snow. */
    private Result fireMeltsSnow(World world, int bx, int by, int bz, int col) {
        Material m = world.getBlockAt(bx + col, by, bz).getType();
        return new Result("fire-melts-snow", m != Material.SNOW_BLOCK, "block=" + m);
    }

    /** FIRE ignites the mob it passes through — it's left burning. */
    private Result fireIgnitesMob(Zombie mob) {
        if (mob == null || !mob.isValid()) {
            return new Result("fire-ignites-mob", false, "no mob");
        }
        return new Result("fire-ignites-mob", mob.getFireTicks() > 0, "fireTicks=" + mob.getFireTicks());
    }

    /** ICE freezes the water on its line to solid ice. */
    private Result iceFreezesWater(World world, int bx, int by, int bz, int col) {
        Material m = world.getBlockAt(bx + col, by, bz).getType();
        return new Result("ice-freezes-water", m == Material.ICE, "block=" + m);
    }

    /** DEPOSIT backfills the air just before its wall terminus with the deposited block. */
    private Result depositFillsAir(World world, int bx, int by, int bz, int col) {
        Material m = world.getBlockAt(bx + col, by, bz).getType();
        return new Result("deposit-fills-air", m == Material.DIRT, "block=" + m);
    }

    /** DEPOSIT + TRAIL fills the empty air it flies through, so a mid-corridor cell is filled. */
    private Result trailFillsPath(World world, int bx, int by, int bz, int col) {
        Material m = world.getBlockAt(bx + col, by, bz).getType();
        return new Result("trail-fills-path", m == Material.DIRT, "block=" + m);
    }

    /** A mining ray through a plant clears the plant AND carves the ground it hid. */
    private Result miningClearsVegetation(World world, int bx, int by, int bz, int col) {
        Material plant = world.getBlockAt(bx + col, by, bz).getType();
        Material ground = world.getBlockAt(bx + col, by - 1, bz).getType();
        boolean ok = plant != Material.SHORT_GRASS && ground != Material.DIRT;
        return new Result("mining-clears-vegetation", ok, "plant=" + plant + " ground=" + ground);
    }

    private Result miningResult(World world, int bx, int by, int bz, int firstDirt, int lastDirt) {
        int broken = 0;
        StringBuilder cells = new StringBuilder();
        for (int dx = firstDirt; dx <= lastDirt; dx++) {
            Material m = world.getBlockAt(bx + dx, by, bz).getType();
            // "Broken" = the dirt is gone. Usually that leaves AIR, but if the
            // corridor spawned next to water/lava the fluid can flow into the
            // cleared cell — still broken, so anything that isn't DIRT counts.
            if (m != Material.DIRT) {
                broken++;
            }
            cells.append(dx == firstDirt ? "" : ",").append(m);
        }
        int total = lastDirt - firstDirt + 1;
        return new Result("mining-breaks-blocks", broken == total,
                broken + "/" + total + " broken [" + cells + "]");
    }

    /**
     * The obsidian plug and everything past it should be intact — a mining ray
     * stops at a block harder than its cap rather than boring forever.
     */
    private Result miningStopsAtHardBlock(World world, int bx, int by, int bz) {
        Material obsidian = world.getBlockAt(bx + 5, by, bz).getType();
        Material past1 = world.getBlockAt(bx + 6, by, bz).getType();
        Material past2 = world.getBlockAt(bx + 7, by, bz).getType();
        boolean ok = obsidian == Material.OBSIDIAN
                && past1 == Material.DIRT && past2 == Material.DIRT;
        return new Result("mining-stops-at-hard-block", ok,
                "obsidian=" + obsidian + " past=[" + past1 + "," + past2 + "]");
    }

    /**
     * A DAMAGE PIERCE bolt fires its burst at every entity it passes through, so
     * <b>both</b> dummies in the line should lose health — not just the first
     * (a non-piercing bolt would stop at the first, and the old shove-only pierce
     * would leave both unharmed).
     */
    private Result pierceDamagesBoth(Zombie a, Zombie b, double a0, double b0) {
        if (a == null || !a.isValid() || b == null || !b.isValid()) {
            return new Result("pierce-hits-each", false, "missing mobs");
        }
        boolean ok = a.getHealth() < a0 && b.getHealth() < b0;
        return new Result("pierce-hits-each", ok,
                String.format("health a %.1f->%.1f b %.1f->%.1f", a0, a.getHealth(), b0, b.getHealth()));
    }

    /** Bare MINING (no PIERCE) breaks the first block it hits and stops. */
    private Result miningAloneStops(World world, int bx, int by, int bz) {
        Material first = world.getBlockAt(bx + 3, by, bz).getType();
        Material second = world.getBlockAt(bx + 4, by, bz).getType();
        boolean ok = first != Material.DIRT && second == Material.DIRT;
        return new Result("mining-alone-stops", ok, "first=" + first + " second=" + second);
    }

    /** EXPAND widens the bore, so the off-axis blocks of the plus also break. */
    private Result expandWidensTunnel(World world, int bx, int by, int bz, int col) {
        Material above = world.getBlockAt(bx + col, by + 1, bz).getType();
        Material beside = world.getBlockAt(bx + col, by, bz + 1).getType();
        boolean ok = above != Material.DIRT && beside != Material.DIRT;
        return new Result("expand-widens-tunnel", ok, "above=" + above + " beside=" + beside);
    }

    /** A PERSIST field re-pulses, so the dummy loses more than a single burst's worth. */
    private Result persistPulsesMultipleTimes(Zombie mob, double before) {
        if (mob == null || !mob.isValid()) {
            return new Result("persist-pulses", false, "no mob");
        }
        double single = firstAoe("DAMAGE").power();
        double taken = before - mob.getHealth();
        return new Result("persist-pulses", taken > single + EPS,
                String.format("took %.1f (single burst %.1f)", taken, single));
    }

    /** A gravity-on bolt fired horizontally should end up below where it launched. */
    private Result gravityFalls(FusionProjectile bolt, double launchY) {
        if (bolt == null) {
            return new Result("gravity-falls", false, "no bolt");
        }
        double dropped = launchY - bolt.positionY();
        return new Result("gravity-falls", dropped > 0.5,
                String.format("dropped %.2f blocks", dropped));
    }

    private void finish(List<Result> results, List<Zombie> spawned, CommandSender sender) {
        for (Zombie z : spawned) {
            if (z != null) {
                z.remove();
            }
        }
        int passed = 0;
        for (Result r : results) {
            if (r.pass()) {
                passed++;
            }
            log.info(TAG + " " + r.name() + ": " + (r.pass() ? "PASS" : "FAIL") + " (" + r.detail() + ")");
        }
        boolean allPass = passed == results.size();
        String summary = "RESULT: " + (allPass ? "PASS" : "FAIL") + " (" + passed + "/" + results.size() + ")";
        log.info(TAG + " " + summary);
        sender.sendMessage(Component.text("Self-test " + summary,
                allPass ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    // ----- helpers -----

    private ProjectileSpec compile(String... ids) {
        return launcher.compile(registry.resolve(List.of(ids)));
    }

    private AoeSpec firstAoe(String... ids) {
        return compile(ids).payload().get(0);
    }

    private boolean approx(double a, double b) {
        return Math.abs(a - b) <= 1.0e-3 * Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
    }

    private String describe(ProjectileSpec spec) {
        StringBuilder sb = new StringBuilder("[");
        List<AoeSpec> payload = spec.payload();
        for (int i = 0; i < payload.size(); i++) {
            AoeSpec a = payload.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a.kind()).append("(r=").append(String.format("%.2f", a.radius()))
                    .append(",p=").append(String.format("%.2f", a.power())).append(")");
        }
        return sb.append("]").toString();
    }

    private Zombie spawnDummy(World world, Location loc, List<Zombie> spawned) {
        try {
            Zombie mob = world.spawn(loc, Zombie.class, z -> {
                z.setAI(false);
                z.setGravity(false);   // stay exactly put so the burst geometry is deterministic
                z.setSilent(true);
                z.setRemoveWhenFarAway(false);
                // Disable hurt-immunity frames so a PERSIST field's re-pulses each
                // register — we're testing that the burst re-fires, not vanilla
                // i-frames (whose 20-tick default equals the persist interval).
                z.setMaximumNoDamageTicks(0);
                z.setNoDamageTicks(0);
            });
            spawned.add(mob);
            return mob;
        } catch (Exception e) {
            log.warning(TAG + " could not spawn dummy: " + e);
            return null;
        }
    }
}
