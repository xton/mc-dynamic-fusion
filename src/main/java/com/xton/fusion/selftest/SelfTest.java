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
 * world — the layer MockBukkit can't reach — so the mechanics that human UAT
 * would check can be verified headlessly, including from the CI smoke boot.
 *
 * <p>Each scenario spawns a disposable mob (or a scratch corridor of blocks),
 * runs an effect, and asserts the world changed as expected: PUSH imparts
 * knockback, DAMAGE lowers health, a MINING ray breaks blocks, and a flight-only
 * shot delivers an empty payload. Results are logged with the sentinel
 * {@code [fusion-selftest] RESULT: PASS|FAIL} that the smoke test greps for.
 *
 * <p>Never runs on its own — it only mutates the world when explicitly invoked,
 * so normal servers are unaffected.
 */
public final class SelfTest {

    private static final String TAG = "[fusion-selftest]";

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

    /** Run all scenarios; report synchronously where possible, finalize after the async mining ray. */
    public void run(CommandSender sender) {
        World world = sender instanceof Player p ? p.getWorld()
                : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
        if (world == null) {
            log.warning(TAG + " RESULT: FAIL (0/0) — no world available");
            sender.sendMessage(Component.text("Self-test: no world available.", NamedTextColor.RED));
            return;
        }
        Location base = sender instanceof Player p ? p.getLocation() : world.getSpawnLocation();
        sender.sendMessage(Component.text("Running fusion self-test — results in the server log ("
                + TAG + ").", NamedTextColor.YELLOW));

        List<Result> results = new ArrayList<>();
        List<Zombie> spawned = new ArrayList<>();
        try {
            results.add(pushKnockback(world, base, spawned));
            results.add(damageHurts(world, base, spawned));
            results.add(payloadOptIn());
        } catch (Exception e) {
            results.add(new Result("setup", false, "exception: " + e));
        }

        // The mining ray flies over several ticks; assert after it has expired,
        // then finalize (this is the single reporting point).
        miningBreaksBlocksAsync(world, base, results, spawned, sender);
    }

    /** PUSH burst imparts outward knockback to a nearby mob. */
    private Result pushKnockback(World world, Location base, List<Zombie> spawned) {
        Location mobLoc = base.clone().add(3, 0, 0);
        Zombie mob = spawnDummy(world, mobLoc, spawned);
        if (mob == null) {
            return new Result("push-knockback", false, "could not spawn mob");
        }
        AoeSpec push = firstAoe("PUSH");
        // Fire the burst offset from the mob so the shove is horizontal.
        burst.fire(world, mobLoc.clone().add(-1.5, 0, 0), push, null);
        double speed = mob.getVelocity().length();
        return new Result("push-knockback", speed > 0.1,
                "velocity=" + String.format("%.3f", speed));
    }

    /** DAMAGE burst lowers a mob's health. */
    private Result damageHurts(World world, Location base, List<Zombie> spawned) {
        Location mobLoc = base.clone().add(3, 0, 2);
        Zombie mob = spawnDummy(world, mobLoc, spawned);
        if (mob == null) {
            return new Result("damage-hurts", false, "could not spawn mob");
        }
        double before = mob.getHealth();
        burst.fire(world, mobLoc, firstAoe("DAMAGE"), null);
        double after = mob.getHealth();
        return new Result("damage-hurts", after < before,
                "health " + before + " -> " + after);
    }

    /** A flight-only stack (MINING) delivers an empty payload; an emitter does not. */
    private Result payloadOptIn() {
        Payload mining = launcher.buildPayload(compile("MINING"));
        Payload push = launcher.buildPayload(compile("PUSH"));
        boolean ok = mining.isEmpty() && !push.isEmpty();
        return new Result("payload-opt-in", ok,
                "mining.empty=" + mining.isEmpty() + " push.empty=" + push.isEmpty());
    }

    /** A MINING ray breaks a scratch corridor of dirt; asserted after it expires. */
    private void miningBreaksBlocksAsync(World world, Location base, List<Result> results,
                                         List<Zombie> spawned, CommandSender sender) {
        int bx = base.getBlockX();
        int by = base.getBlockY() + 1;
        int bz = base.getBlockZ();
        final int firstDirt = 3;
        final int lastDirt = 7;
        boolean setupOk = true;
        try {
            // Clear the approach and set a known dirt run in front along +X.
            for (int dx = 0; dx <= lastDirt + 1; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.AIR, false);
            }
            for (int dx = firstDirt; dx <= lastDirt; dx++) {
                world.getBlockAt(bx + dx, by, bz).setType(Material.DIRT, false);
            }
            ProjectileSpec spec = compile("MINING");
            Payload payload = launcher.buildPayload(spec);
            Location origin = new Location(world, bx + 0.5, by + 0.5, bz + 0.5);
            Vector velocity = new Vector(1, 0, 0).multiply(spec.speed());
            new FusionProjectile(launcher.plugin(), payload, spec, world, origin, velocity, null, 0)
                    .start();
        } catch (Exception e) {
            setupOk = false;
            results.add(new Result("mining-breaks-blocks", false, "setup exception: " + e));
        }

        final boolean fireable = setupOk;
        // Mining lifetime is short (~6t); assert well after it has expired.
        scheduler.runLater(() -> {
            if (fireable) {
                int broken = 0;
                for (int dx = firstDirt; dx <= lastDirt; dx++) {
                    if (world.getBlockAt(bx + dx, by, bz).getType() == Material.AIR) {
                        broken++;
                    }
                }
                int total = lastDirt - firstDirt + 1;
                results.add(new Result("mining-breaks-blocks", broken == total,
                        broken + "/" + total + " blocks broken"));
            }
            finish(results, spawned, sender);
        }, 25);
    }

    private void finish(List<Result> results, List<Zombie> spawned, CommandSender sender) {
        for (Zombie z : spawned) {
            z.remove();
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

    /** The first AOE element a single-emitter stack produces. */
    private AoeSpec firstAoe(String id) {
        return compile(id).payload().get(0);
    }

    private Zombie spawnDummy(World world, Location loc, List<Zombie> spawned) {
        Zombie mob = world.spawn(loc, Zombie.class, z -> {
            z.setAI(false);         // stay put; velocity/health still respond
            z.setSilent(true);
            z.setRemoveWhenFarAway(false);
        });
        spawned.add(mob);
        return mob;
    }
}
