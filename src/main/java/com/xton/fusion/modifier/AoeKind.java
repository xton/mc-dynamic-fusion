package com.xton.fusion.modifier;

/** What an area-of-effect element does to the entities it catches. */
public enum AoeKind {
    /** Shoves entities away from the centre (INVERT pulls them in). */
    PUSH,
    /** Damages entities in range. */
    DAMAGE
}
