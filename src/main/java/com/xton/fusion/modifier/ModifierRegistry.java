package com.xton.fusion.modifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Maps modifier IDs to their implementations. */
public final class ModifierRegistry {

    private final Map<String, Modifier> byId = new LinkedHashMap<>();

    public ModifierRegistry register(Modifier modifier) {
        byId.put(modifier.id(), modifier);
        return this;
    }

    public Optional<Modifier> get(String id) {
        return Optional.ofNullable(resolveOne(id));
    }

    public boolean isKnown(String id) {
        return resolveOne(id) != null;
    }

    /**
     * Resolve a single ID to its implementation, or null if none. A plain ID is a
     * direct lookup; a {@code BASE:PARAM} ID (e.g. {@code DEPOSIT:DIRT}) resolves
     * the {@link ParameterizedModifier} template registered under {@code BASE} and
     * asks it to mint the concrete instance.
     */
    private Modifier resolveOne(String id) {
        Modifier direct = byId.get(id);
        if (direct != null) {
            return direct;
        }
        int colon = id.indexOf(':');
        if (colon > 0 && byId.get(id.substring(0, colon)) instanceof ParameterizedModifier template) {
            return template.withParameter(id.substring(colon + 1));
        }
        return null;
    }

    /** All registered modifier IDs, in registration order. */
    public Set<String> ids() {
        return new LinkedHashSet<>(byId.keySet());
    }

    /**
     * Resolve an ordered list of IDs into a stack, preserving order and
     * duplicates and silently skipping IDs that have no implementation.
     */
    public ModifierStack resolve(List<String> ids) {
        List<Modifier> resolved = new ArrayList<>(ids.size());
        for (String id : ids) {
            Modifier m = resolveOne(id);
            if (m != null) {
                resolved.add(m);
            }
        }
        return new ModifierStack(resolved);
    }
}
