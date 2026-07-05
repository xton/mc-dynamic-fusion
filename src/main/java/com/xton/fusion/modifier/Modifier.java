package com.xton.fusion.modifier;

/**
 * A single composable weapon property. There are two flavours (see
 * {@link Category}): <b>emitters</b> add a concrete element to the weapon (an
 * entity burst, an environmental sweep, a spawned child projectile, a launched
 * mob, ...); <b>transforms</b> modify the nearest preceding emitter or the
 * projectile's flight (scale it, pierce it, extend its life).
 *
 * <p>Implementations act on a {@link WeaponBuilder}, which threads the RPN
 * compile state (current projectile + its payload). They stay free of Bukkit
 * world interaction, so the whole compile is server-free and unit-testable; the
 * projectile/burst layer reads the compiled {@link ProjectileSpec} and acts on
 * the world.
 */
public interface Modifier {

    /** Whether this modifier adds an element or transforms the previous one. */
    enum Category { EMITTER, TRANSFORM }

    /** Stable identifier baked into PDC (e.g. {@code "PUSH"}). */
    String id();

    /** Short human-readable name shown in lore (e.g. {@code "Push"}). */
    String displayName();

    /** One-line description shown next to the name in lore. */
    String description();

    /** Longer description for hover text. */
    String detailedDescription();

    /** Emitter or transform — used for lore grouping and clarity. */
    Category category();

    /** Act on the compile state (add an emitter, or transform the previous one). */
    void apply(WeaponBuilder builder);
}
