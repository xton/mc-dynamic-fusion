package com.xton.fusion.modifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Maps modifier IDs to their implementations. */
public final class ModifierRegistry {

    private final Map<String, Modifier> byId = new LinkedHashMap<>();

    public ModifierRegistry register(Modifier modifier) {
        byId.put(modifier.id(), modifier);
        return this;
    }

    public Optional<Modifier> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean isKnown(String id) {
        return byId.containsKey(id);
    }

    /**
     * Resolve an ordered list of IDs into a stack, preserving order and
     * duplicates and silently skipping IDs that have no implementation.
     */
    public ModifierStack resolve(List<String> ids) {
        List<Modifier> resolved = new ArrayList<>(ids.size());
        for (String id : ids) {
            Modifier m = byId.get(id);
            if (m != null) {
                resolved.add(m);
            }
        }
        return new ModifierStack(resolved);
    }
}
