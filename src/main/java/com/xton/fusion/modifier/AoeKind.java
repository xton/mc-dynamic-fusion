package com.xton.fusion.modifier;

/**
 * What an area-of-effect element does. Two families:
 *
 * <ul>
 *   <li><b>entity bursts</b> ({@link #PUSH}, {@link #DAMAGE}) — affect entities
 *       in a radius, delivered as a burst at the terminus (and at each pierced
 *       entity);</li>
 *   <li><b>environmental</b> ({@link #MINING}, {@link #FIRE}, {@link #ICE},
 *       {@link #DEPOSIT}) — act on the blocks (and, for FIRE/ICE, entities) in a
 *       radius, applied along the flight: at every occupied cell if the shot
 *       PIERCEs, every empty cell if it TRAILs, and always at the terminus.</li>
 * </ul>
 */
public enum AoeKind {
    /** Shoves entities away from the centre (INVERT, or PULL, drags them in). */
    PUSH,
    /** Damages entities in range. */
    DAMAGE,
    /** Heals entities in range — the complement of {@link #DAMAGE}. */
    HEAL,
    /** Breaks the soft blocks in radius (a tunnel cross-section, widened by EXPAND). */
    MINING,
    /** Sets fire in radius: spreads fire, melts snow/ice, ignites mobs. */
    FIRE,
    /** Freezes in radius: water→ice, lava→obsidian, puts out fire, chills mobs. */
    ICE,
    /** Fills the empty cells in radius with a block ({@code material}). */
    DEPOSIT,
    /** A sensor, not a burst: marks a DETECT child's trigger radius (widened by EXPAND). */
    DETECT;

    /** True for the block/environment-affecting kinds applied along the flight. */
    public boolean isEnvironmental() {
        return this == MINING || this == FIRE || this == ICE || this == DEPOSIT;
    }
}
