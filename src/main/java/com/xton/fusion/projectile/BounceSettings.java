package com.xton.fusion.projectile;

/**
 * Tunables for BOUNCE's energy loss per bounce off a block — {@code restitution}
 * is how much speed a bounce retains overall, {@code floorFriction} how much
 * extra horizontal drag a floor bounce adds on top of that (so a shot rolls to
 * a stop rather than skating indefinitely).
 */
public record BounceSettings(double restitution, double floorFriction) {
}
