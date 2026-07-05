package com.xton.fusion.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.World;

/**
 * Global gate for which worlds fusion effects are allowed to fire in. Empty
 * (the default) is unrestricted — every world is allowed. Non-empty means
 * ONLY the named worlds are allowed: a fused item carried into any other
 * world (a portal, a plugin teleport) keeps its modifiers — nothing strips
 * its PDC tags — but those modifiers simply won't trigger there. A sword
 * falls back to a plain vanilla swing, a bow to a plain vanilla arrow, worn
 * effects (GLOW, LIFT) just don't apply.
 */
public final class WorldFilter {

    private final Set<String> allowedWorlds;

    public WorldFilter(List<String> allowedWorlds) {
        this.allowedWorlds = allowedWorlds.isEmpty() ? Set.of() : new HashSet<>(allowedWorlds);
    }

    public boolean isAllowed(World world) {
        return allowedWorlds.isEmpty() || (world != null && allowedWorlds.contains(world.getName()));
    }
}
