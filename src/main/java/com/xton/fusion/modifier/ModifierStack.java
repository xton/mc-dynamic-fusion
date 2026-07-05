package com.xton.fusion.modifier;

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
        return modifiers.stream().map(Modifier::id).toList();
    }

    public boolean contains(String id) {
        return modifiers.stream().anyMatch(m -> m.id().equals(id));
    }

    public boolean isEmpty() {
        return modifiers.isEmpty();
    }
}
