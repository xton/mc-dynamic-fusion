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
 * only). It exercises the <em>real</em> projectile and burst code against a live
 * world — the gameplay layer MockBukkit can't reach — so the mechanics a human
 * UAT would check can be verified headlessly, including from the CI smoke boot.
 *
 * <p>The scenarios are <b>phased across ticks</b>: freshly spawned entities are
 * not returned by {@code getNearbyEntities} until a later tick, so we spawn on
 * tick 0, act on the dummies a few ticks later, and assert after the async
 * mining ray has flown. Each result is logged with the sentinel
 * {@code [fusion-selftest] RESULT: PASS|FAIL} that the smoke test greps for.
 *
 * <p>Never runs on its own — it only mutates the world when explicitly invoked,
 * so normal servers are unaffected.
 */
public final class SelfTest {

    private static final String TAG = "[fusion-selftest]";
    /** Ticks to let a spawned entity / placed block register before we act. */
    private static final long SETTLE = 3;
    /** Ticks to let the mining ray finish flying before we assert. */
    private static final long MINING_WAIT = 30;

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

        // --- tick 0: spawn dummies and lay the mining corridor ---
        Zombie pushMob = spawnDummy(world, base.clone().add(3, 0, 0), spawned);
        Zombie dmgMob = spawnDummy(world, base.clone().add(3, 0, 2), spawned);
        results.add(payloadOptIn()); // pure; safe to record immediately

        int bx = base.getBlockX();
        int by = base.getBlockY() + 1;
        int bz = base.getBlockZ();
        final int firstDirt = 3;
        final int lastDirt = 7;
        boolean corridorOk = layCorridor(world, bx, by, bz, firstDirt, lastDirt, results);

        // --- tick SETTLE: dummies + blocks are registered now; act on them ---
        final boolean fireMining = corridorOk;
        scheduler.runLater(() -> {
            results.add(pushKnockback(world, pushMob));
            results.add(damageHurts(world, dmgMob));
            if (fireMining) {
                fireMiningRay(world, bx, by, bz);
            }
        }, SETTLE);

        // --- after the ray has flown: assert blocks, then finalize ---
        scheduler.runLater(() -> {
            if (fireMining) {
                results.add(miningResult(world, bx, by, bz, firstDirt, lastDirt));
            }
            finish(results, spawned, sender);
            forceLoad(world, ccx, ccz, false); // release the chunk tickets
        }, SETTLE + MINING_WAIT);
    }

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

    /** A flight-only stack (MINING) delivers an empty payload; an emitter does not. */
    private Result payloadOptIn() {
        Payload mining = launcher.buildPayload(compile("MINING"));
        Payload push = launcher.buildPayload(compile("PUSH"));
        boolean ok = mining.isEmpty() && !push.isEmpty();
        return new Result("payload-opt-in", ok,
                "mining.empty=" + mining.isEmpty() + " push.empty=" + push.isEmpty());
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

    /** Launch a MINING ray down the corridor from just before the dirt run. */
    private void fireMiningRay(World world, int bx, int by, int bz) {
        ProjectileSpec spec = compile("MINING");
        Payload payload = launcher.buildPayload(spec);
        Location origin = new Location(world, bx + 0.5, by + 0.5, bz + 0.5);
        Vector velocity = new Vector(1, 0, 0).multiply(spec.speed());
        new FusionProjectile(launcher.plugin(), payload, spec, world, origin, velocity, null, 0).start();
        log.info(TAG + " mining: fired ray speed=" + spec.speed()
                + " life=" + spec.lifetimeTicks() + " mining=" + spec.isMining()
                + " pierce=" + spec.isPierce());
    }

    private Result miningResult(World world, int bx, int by, int bz, int firstDirt, int lastDirt) {
        int broken = 0;
        StringBuilder cells = new StringBuilder();
        for (int dx = firstDirt; dx <= lastDirt; dx++) {
            Material m = world.getBlockAt(bx + dx, by, bz).getType();
            if (m == Material.AIR) {
                broken++;
            }
            cells.append(dx == firstDirt ? "" : ",").append(m);
        }
        int total = lastDirt - firstDirt + 1;
        return new Result("mining-breaks-blocks", broken == total,
                broken + "/" + total + " broken [" + cells + "]");
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

    private AoeSpec firstAoe(String id) {
        return compile(id).payload().get(0);
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
