package com.xton.fusion.projectile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * TELEPORT's dash: instead of an instant blink, slides the caster to the
 * destination over a handful of ticks (a fast zoom, not a snap), invulnerable
 * for the transit so a mid-dash mob hit or fall doesn't punish it. The
 * caster's own look direction is preserved each step (they can still look
 * around mid-zoom); only the position is interpolated.
 *
 * <p>The per-Shot teleport latch only guards against multiple teleports
 * <em>within one cast</em> — two separate casts (e.g. two swings landing
 * closer together than {@code totalTicks}) can still each spawn their own
 * zoom for the same player. {@link #GRANTS} makes overlapping zooms safe:
 * only the first one to start captures the caster's <em>real</em> prior
 * invulnerability, and only the last one to finish restores it — otherwise a
 * second zoom starting mid-flight would capture "invulnerable" (the first
 * zoom's own grant) as if it were the original state, and leave the caster
 * stuck invulnerable forever once both finish.
 */
final class TeleportZoomTask extends BukkitRunnable {

    /** One player's currently-held invulnerability grant, shared by every zoom in flight for them. */
    private static final class Grant {
        final boolean original;
        int depth = 1;

        Grant(boolean original) {
            this.original = original;
        }
    }

    private static final Map<UUID, Grant> GRANTS = new HashMap<>();

    private final Player caster;
    private final Vector from;
    private final Vector to;
    private final int totalTicks;
    private int age;

    TeleportZoomTask(Player caster, Location from, Location to, int totalTicks) {
        this.caster = caster;
        this.from = from.toVector();
        this.to = to.toVector();
        this.totalTicks = Math.max(1, totalTicks);
        grantInvulnerability(caster);
    }

    /** One more zoom now relies on invulnerability; capture the real original only on the first. */
    private static void grantInvulnerability(Player caster) {
        Grant grant = GRANTS.get(caster.getUniqueId());
        if (grant == null) {
            GRANTS.put(caster.getUniqueId(), new Grant(caster.isInvulnerable()));
            caster.setInvulnerable(true);
        } else {
            grant.depth++;
        }
    }

    /** One zoom is done; once every overlapping zoom has finished, restore the real original. */
    private static void releaseInvulnerability(Player caster) {
        Grant grant = GRANTS.get(caster.getUniqueId());
        if (grant == null) {
            return; // defensive: nothing to release
        }
        if (--grant.depth <= 0) {
            GRANTS.remove(caster.getUniqueId());
            caster.setInvulnerable(grant.original);
        }
    }

    @Override
    public void run() {
        age++;
        double t = Math.min(1.0, (double) age / totalTicks);
        Vector at = from.clone().add(to.clone().subtract(from).multiply(t));
        Location look = caster.getLocation();
        caster.teleport(at.toLocation(caster.getWorld(), look.getYaw(), look.getPitch()));
        if (age >= totalTicks) {
            caster.setVelocity(new Vector(0, 0, 0));
            releaseInvulnerability(caster);
            cancel();
        }
    }
}
