package com.xton.fusion.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;

import com.xton.fusion.modifier.AoeKind;
import com.xton.fusion.modifier.AoeSpec;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.ProjectileSpec;
import com.xton.fusion.projectile.AoeBurst;
import com.xton.fusion.projectile.FusionProjectile;
import com.xton.fusion.projectile.Payload;
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
        Zombie persistMob = spawnDummy(world, base.clone().add(3, 0, -10), spawned);
        final double persist0 = health(persistMob);

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

        final double gravityLaunchY = by + 20;
        final FusionProjectile[] gravityBolt = new FusionProjectile[1];

        // --- tick SETTLE: dummies + blocks are registered now; act on them ---
        final boolean fireMining = corridorOk;
        final boolean fireHard = hardOk;
        final boolean fireAlone = aloneOk;
        final boolean firePlus = plusOk;
        scheduler.runLater(() -> {
            results.add(pushKnockback(world, pushMob));
            results.add(damageHurts(world, dmgMob));
            results.add(invertPullsInward(world, invertMob));
            results.add(chainHopsToSecond(world, chainNear, chainFar));
            if (persistMob != null && persistMob.isValid()) {
                burst.fire(world, persistMob.getLocation(), firstAoe("DAMAGE", "PERSIST"), null);
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
            gravityBolt[0] = fireBolt(world,
                    new Location(world, bx + 0.5, gravityLaunchY, bz + 0.5), PLUS_X, true);
        }, SETTLE);

        // Shortly after firing: the piercing DAMAGE bolt has hit both dummies.
        scheduler.runLater(() -> results.add(pierceDamagesBoth(pierceA, pierceB, pierceA0, pierceB0)),
                SETTLE + 8);

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
        r.add(new Result("compile:mining-and-pierce", flagsOk,
                "mining.pierce=" + mining.isPierce() + " miningPierce.pierce=" + miningPierce.isPierce()
                        + " mining.r=" + mining.miningAoe().radius()
                        + " miningExpand.r=" + miningExpand.miningAoe().radius()));

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
        Payload payload = launcher.buildPayload(spec);
        Vector velocity = dir.clone().normalize().multiply(spec.speed());
        FusionProjectile bolt = new FusionProjectile(
                launcher.plugin(), payload, spec, world, origin, velocity, null, 0);
        bolt.start();
        return bolt;
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
