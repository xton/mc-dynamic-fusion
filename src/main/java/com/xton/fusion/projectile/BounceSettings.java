package com.xton.fusion.projectile;

/**
 * Tunables for BOUNCE's energy loss per bounce off a block — {@code restitution}
 * is how much speed a bounce retains overall, {@code floorFriction} how much
 * extra horizontal drag a floor bounce adds on top of that (so a shot rolls to
 * a stop rather than skating indefinitely). {@code restSpeed} is how slow (in
 * blocks/tick) it has to be crawling before it's considered settled and goes
 * armed rather than still rattling.
 */
public record BounceSettings(double restitution, double floorFriction, double restSpeed) {
}
