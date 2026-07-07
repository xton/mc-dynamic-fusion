package com.xton.fusion.modifier;

import java.util.List;

/**
 * A modifier whose behaviour is tuned by a parameter carried in its ID after a
 * colon — e.g. {@code DEPOSIT:DIRT}. The registry stores one <em>template</em>
 * (keyed by the bare base ID, {@code DEPOSIT}) and, when it resolves a
 * {@code BASE:PARAM} ID, calls {@link #withParameter} to mint the concrete
 * instance that the compiler and lore then use.
 */
public interface ParameterizedModifier extends Modifier {

    /**
     * Build a concrete instance bound to {@code param} (the text after the colon,
     * e.g. {@code "DIRT"}), or {@code null} if the parameter is invalid.
     */
    Modifier withParameter(String param);

    /**
     * Candidate parameter values (just the part after the colon, e.g.
     * {@code "COW"}) whose name starts with {@code prefix} (case-insensitive),
     * for {@code /fusion give} tab-completion. Empty by default — override
     * wherever the valid parameters are enumerable.
     */
    default List<String> parameterCompletions(String prefix) {
        return List.of();
    }
}
