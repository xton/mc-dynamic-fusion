package com.xton.fusion.modifier;

/** What an area-of-effect element does. */
public enum AoeKind {
    /** Shoves entities away from the centre (INVERT pulls them in). */
    PUSH,
    /** Damages entities in range. */
    DAMAGE,
    /**
     * Breaks the soft blocks around the projectile's path (its {@code radius} is
     * the tunnel cross-section, widened by EXPAND). Unlike PUSH/DAMAGE this is
     * applied along the flight by the projectile itself, not as a terminus
     * burst, so it is not built into the delivered payload.
     */
    MINING
}
