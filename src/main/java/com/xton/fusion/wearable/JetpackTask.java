package com.xton.fusion.wearable;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.util.WorldFilter;

/**
 * The Jetpack: a player wearing a fused chestplate/elytra with LIFT gets
 * directional thruster control while airborne, not an elytra glide (vanilla
 * gliding is blocked outright for a LIFT item — see {@link JetpackGlideListener}
 * — so its look-tied auto-forward never fights this).
 *
 * <p>Holding jump ramps vertical velocity up to a capped maximum — a hover/
 * ascend, not a single jump-boost; holding crouch overrides that and lets
 * gravity take back over, so pressing jump again resumes the climb. Forward/
 * back/strafe-left/right each nudge horizontal velocity in whichever direction
 * the player is <em>currently</em> facing — world-space momentum, not
 * look-locked, so turning around doesn't redirect speed already built up; only
 * fresh thrust does. A normal jump from the ground is untouched (this only acts
 * once the player has left the ground).
 *
 * <p>Ticked every tick (not on the coarser worn-effect cadence) so the ramp
 * feels smooth and responsive to the held keys, via
 * {@link Player#getCurrentInput()} — Paper's per-tick snapshot of the client's
 * raw movement/jump input, distinct from the discrete {@code PlayerJumpEvent} a
 * single jump fires.
 *
 * <p>Sustained airborne movement with no vanilla-recognized cause (gliding,
 * creative/spectator, levitation, ...) trips the server's own anti-fly check
 * and kicks the player — since we deliberately block real gliding (see
 * {@link JetpackGlideListener}), we have to supply that exemption ourselves:
 * {@link Player#setAllowFlight} is granted the moment the jetpack engages and
 * revoked the moment it doesn't, so a survival player is never left with a
 * standing "you can fly" permission (creative/spectator players, who already
 * have it legitimately, are left alone either way).
 *
 * <p>Granting AllowFlight only silences that kick — it doesn't have to mean the
 * player ever actually <em>uses</em> real flight. The client's own double-tap-
 * space gesture to toggle it on is a distinct, cancellable action
 * ({@code PlayerToggleFlightEvent}), and that transition into real flight is
 * blocked outright for a LIFT wearer — see {@link JetpackFlightListener} — the
 * exact same "cancel the toggle into the state that would fight the jetpack"
 * treatment {@link JetpackGlideListener} already gives vanilla gliding. Real
 * flight ({@link Player#isFlying()}) suppresses gravity and fall damage
 * outright, which isn't the jetpack's deal — it's thrust, not immunity — so
 * blocking the toggle at the source keeps that from ever engaging, rather than
 * reacting to it (and risking a window where it's briefly live) after the fact.
 */
public final class JetpackTask implements Runnable {

    private final FusedItemReader reader;
    private final double thrustPerTick;
    private final double maxVelocity;
    private final double lateralThrustPerTick;
    private final double lateralMaxVelocity;
    private final WorldFilter worldFilter;

    public JetpackTask(FusedItemReader reader, double thrustPerTick, double maxVelocity,
                       double lateralThrustPerTick, double lateralMaxVelocity, WorldFilter worldFilter) {
        this.reader = reader;
        this.thrustPerTick = thrustPerTick;
        this.maxVelocity = maxVelocity;
        this.lateralThrustPerTick = lateralThrustPerTick;
        this.lateralMaxVelocity = lateralMaxVelocity;
        this.worldFilter = worldFilter;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean active = !player.isOnGround() && WornLift.isWorn(reader, player)
                    && worldFilter.isAllowed(player.getWorld());
            if (!active) {
                // grounded jumps stay vanilla; revoke the flight exemption we
                // grant below the moment it's no longer earned (never touch a
                // creative/spectator player's own legitimate flight).
                if (player.getAllowFlight() && player.getGameMode() != GameMode.CREATIVE
                        && player.getGameMode() != GameMode.SPECTATOR) {
                    player.setAllowFlight(false);
                }
                continue;
            }
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            // Real flight itself is blocked at the toggle (see JetpackFlightListener),
            // not reacted to here — AllowFlight only ever silences the anti-fly kick.
            Input input = player.getCurrentInput();
            Vector v = player.getVelocity();
            boolean changed = false;

            if (input.isSneak()) {
                // crouch brakes the climb and lets gravity take back over; jump resumes it
            } else if (input.isJump() && v.getY() < maxVelocity) {
                v.setY(Math.min(maxVelocity, v.getY() + thrustPerTick));
                changed = true;
            }

            Vector thrust = lateralThrust(player, input);
            if (thrust.lengthSquared() > 0) {
                Vector horizontal = new Vector(v.getX(), 0, v.getZ()).add(thrust);
                double speed = horizontal.length();
                if (speed > lateralMaxVelocity) {
                    horizontal.multiply(lateralMaxVelocity / speed);
                }
                v.setX(horizontal.getX());
                v.setZ(horizontal.getZ());
                changed = true;
            }

            if (changed) {
                player.setVelocity(v);
            }
        }
    }

    /**
     * Thrust in the player's <em>current</em> facing direction (flattened to the
     * horizontal plane), combining whichever of forward/back/left/right are held.
     * Opposing keys (e.g. forward+back) cancel to no thrust.
     */
    private Vector lateralThrust(Player player, Input input) {
        if (!input.isForward() && !input.isBackward() && !input.isLeft() && !input.isRight()) {
            return new Vector(0, 0, 0);
        }
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-6) {
            forward = new Vector(0, 0, 1); // looking straight up/down: pick an arbitrary forward
        } else {
            forward.normalize();
        }
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        Vector dir = new Vector(0, 0, 0);
        if (input.isForward()) {
            dir.add(forward);
        }
        if (input.isBackward()) {
            dir.subtract(forward);
        }
        if (input.isRight()) {
            dir.add(right);
        }
        if (input.isLeft()) {
            dir.subtract(right);
        }
        if (dir.lengthSquared() < 1.0e-9) {
            return new Vector(0, 0, 0);
        }
        return dir.normalize().multiply(lateralThrustPerTick);
    }
}
