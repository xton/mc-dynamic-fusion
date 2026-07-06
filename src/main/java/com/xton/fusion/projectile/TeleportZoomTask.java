package com.xton.fusion.projectile;

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
 */
final class TeleportZoomTask extends BukkitRunnable {

    private final Player caster;
    private final Vector from;
    private final Vector to;
    private final int totalTicks;
    private final boolean wasInvulnerable;
    private int age;

    TeleportZoomTask(Player caster, Location from, Location to, int totalTicks) {
        this.caster = caster;
        this.from = from.toVector();
        this.to = to.toVector();
        this.totalTicks = Math.max(1, totalTicks);
        this.wasInvulnerable = caster.isInvulnerable();
        caster.setInvulnerable(true);
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
            caster.setInvulnerable(wasInvulnerable);
            cancel();
        }
    }
}
