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

        // --- tick 0: spawn dummies and lay the mining corridors ---
        Zombie pushMob = spawnDummy(world, base.clone().add(3, 0, 0), spawned);
        Zombie dmgMob = spawnDummy(world, base.clone().add(3, 0, 4), spawned);
        Zombie invertMob = spawnDummy(world, base.clone().add(3, 0, 8), spawned);
        Zombie chainNear = spawnDummy(world, base.clone().add(3, 0, 12), spawned);
        Zombie chainFar = spawnDummy(world, base.clone().add(3, 0, 15), spawned);
        // Two dummies in a line along +X, on their own cleared row, for PIERCE.
        Zombie pierceA = spawnDummy(world, base.clone().add(2, 0, -4), spawned);
        Zombie pierceB = spawnDummy(world, base.clone().add(5, 0, -4), spawned);

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

        // --- tick SETTLE: dummies + blocks are registered now; act on them ---
        final boolean fireMining = corridorOk;
        final boolean fireHard = hardOk;
        scheduler.runLater(() -> {
            results.add(pushKnockback(world, pushMob));
            results.add(damageHurts(world, dmgMob));
            results.add(invertPullsInward(world, invertMob));
            results.add(chainHopsToSecond(world, chainNear, chainFar));
            if (fireMining) {
                fireMiningRay(world, bx, by, bz);
            }
            if (fireHard) {
                fireMiningRay(world, bx, by, bz - 2);
            }
            firePierceBolt(world, bx, by, bz - 4);
        }, SETTLE);

        // A few ticks after firing, the piercing bolt has passed through both
        // dummies and shoved each — capture that before drag bleeds it off. (The
        // dummies are frozen for deterministic geometry, so they don't move; the
        // imparted velocity is the observable that it did not stop at the first.)
        scheduler.runLater(() -> results.add(piercePassesThroughBoth(pierceA, pierceB)),
                SETTLE + 8);

        // --- after the shots have flown: assert blocks, then finalize ---
        scheduler.runLater(() -> {
            if (fireMining) {
                results.add(miningResult(world, bx, by, bz, firstDirt, lastDirt));
            }
            if (fireHard) {
                results.add(miningStopsAtHardBlock(world, bx, by, bz - 2));
            }
            finish(results, spawned, sender);
            forceLoad(world, ccx, ccz, false); // release the chunk tickets
        }, SETTLE + MINING_WAIT);
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

        // Emitters seed a payload element; a flight-only / transform-only stack
        // delivers nothing (burst is opt-in — no terminus pop).
        boolean miningEmpty = compile("MINING").payload().isEmpty();
        boolean pushNonEmpty = !compile("PUSH").payload().isEmpty();
        boolean transformOnlyEmpty = compile("EXPAND").payload().isEmpty()
                && compile("AMPLIFY").payload().isEmpty()
                && compile("INVERT").payload().isEmpty();
        r.add(new Result("compile:payload-opt-in", miningEmpty && pushNonEmpty && transformOnlyEmpty,
                "mining.empty=" + miningEmpty + " push.nonEmpty=" + pushNonEmpty
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

        // Flight flags: PIERCE sets pierce (not mining); MINING sets both, with a
        // short life and faster speed than a plain bolt (a fast, short ray).
        ProjectileSpec pierce = compile("DAMAGE", "PIERCE");
        ProjectileSpec bare = compile("DAMAGE");
        ProjectileSpec mining = compile("MINING");
        boolean flagsOk = pierce.isPierce() && !pierce.isMining()
                && mining.isMining() && mining.isPierce()
                && mining.lifetimeTicks() < bare.lifetimeTicks()
                && mining.speed() > bare.speed();
        r.add(new Result("compile:flight-flags", flagsOk,
                "pierce.pierce=" + pierce.isPierce() + " mining.mining=" + mining.isMining()
                        + " mining.life=" + mining.lifetimeTicks() + " bare.life=" + bare.lifetimeTicks()
                        + " mining.speed=" + mining.speed() + " bare.speed=" + bare.speed()));

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

    /** Launch a MINING ray down the corridor at row {@code bz} from just before it. */
    private void fireMiningRay(World world, int bx, int by, int bz) {
        ProjectileSpec spec = compile("MINING");
        Payload payload = launcher.buildPayload(spec);
        Location origin = new Location(world, bx + 0.5, by + 0.5, bz + 0.5);
        Vector velocity = new Vector(1, 0, 0).multiply(spec.speed());
        new FusionProjectile(launcher.plugin(), payload, spec, world, origin, velocity, null, 0).start();
        log.info(TAG + " mining: fired ray at z=" + bz + " speed=" + spec.speed()
                + " life=" + spec.lifetimeTicks() + " mining=" + spec.isMining()
                + " pierce=" + spec.isPierce());
    }

    /** Launch a PIERCE bolt down row {@code bz}; it should pass through both dummies. */
    private void firePierceBolt(World world, int bx, int by, int bz) {
        ProjectileSpec spec = compile("PIERCE");
        Payload payload = launcher.buildPayload(spec);
        Location origin = new Location(world, bx + 0.5, by + 0.5, bz + 0.5);
        Vector velocity = new Vector(1, 0, 0).multiply(spec.speed());
        new FusionProjectile(launcher.plugin(), payload, spec, world, origin, velocity, null, 0).start();
        log.info(TAG + " pierce: fired bolt at z=" + bz + " speed=" + spec.speed()
                + " pierce=" + spec.isPierce());
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
     * Both dummies should carry a velocity from the piercing bolt's contact
     * shove — proof it passed through the first and reached the second rather
     * than stopping. (A non-piercing bolt would halt at the first and never
     * touch the second.)
     */
    private Result piercePassesThroughBoth(Zombie a, Zombie b) {
        if (a == null || !a.isValid() || b == null || !b.isValid()) {
            return new Result("pierce-passes-through-both", false, "missing mobs");
        }
        double va = a.getVelocity().length();
        double vb = b.getVelocity().length();
        return new Result("pierce-passes-through-both", va > 0.05 && vb > 0.05,
                String.format("velocity a=%.3f b=%.3f", va, vb));
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
            });
            spawned.add(mob);
            return mob;
        } catch (Exception e) {
            log.warning(TAG + " could not spawn dummy: " + e);
            return null;
        }
    }
}
