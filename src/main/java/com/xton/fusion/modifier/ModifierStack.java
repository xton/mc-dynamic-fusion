package com.xton.fusion.modifier;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of resolved modifiers. Duplicates are intentionally
 * <em>not</em> removed — repeats stack and order is load-bearing.
 */
public final class ModifierStack {

    private final List<Modifier> modifiers;

    public ModifierStack(List<Modifier> modifiers) {
        this.modifiers = List.copyOf(modifiers);
    }

    public List<Modifier> modifiers() {
        return modifiers;
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>(modifiers.size());
        for (Modifier m : modifiers) {
            ids.add(m.id());
        }
        return ids;
    }

    public boolean contains(String id) {
        for (Modifier m : modifiers) {
            if (m.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return modifiers.isEmpty();
    }

    /** Apply every modifier in order, threading the context through each. */
    public ModifierContext applyTo(ModifierContext ctx) {
        ModifierContext current = ctx;
        for (Modifier m : modifiers) {
            current = m.apply(current);
        }
        return current;
    }
}
