package com.xton.fusion.modifier;

/**
 * How a projectile renders its flight wake. The weapon type seeds the default
 * (a bow shot is {@link #BRIGHT}, a melee poke {@link #SUBTLE}); the VISIBLE and
 * INVISIBLE modifiers override it outright.
 */
public enum TrailStyle {
    /** No trail at all — a truly unseen bolt (INVISIBLE). */
    HIDDEN,
    /** A faint energy-ball wake: reads on a long shot, doesn't clutter a swing (melee default). */
    SUBTLE,
    /** The full bright wake, plus mining sparks (ranged default, or VISIBLE). */
    BRIGHT
}
