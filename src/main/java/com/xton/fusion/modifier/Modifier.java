package com.xton.fusion.modifier;

/**
 * A single composable weapon property. Implementations mutate the
 * {@link ModifierContext} they are handed and return it.
 *
 * <p>Modifiers should stay free of Bukkit world interaction where possible:
 * they compute effect parameters into the context, and the weapon behaviour
 * layer reads the resolved context and acts on the world. The {@code NOVA}
 * modifier in Phase Zero is fully server-free and unit-testable.
 */
public interface Modifier {

    /** Stable identifier baked into PDC (e.g. {@code "NOVA"}). */
    String id();

    /** Short human-readable name shown in lore (e.g. {@code "Nova"}). */
    String displayName();

    /** One-line description shown next to the name in lore. */
    String description();

    /** Longer description for hover text. */
    String detailedDescription();

    /** Transform the context and return it. */
    ModifierContext apply(ModifierContext ctx);
}
