package com.xton.fusion.modifier.impl;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.entity.EntityType;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ParameterizedModifier;
import com.xton.fusion.modifier.WeaponBuilder;
import com.xton.fusion.util.Format;

/**
 * Emitter: launches a live entity as the projectile — the Cow Launcher. The type
 * is carried in the ID after a colon ({@code MOB:COW}, {@code MOB:SHEEP}), spawned
 * at the muzzle with the shot's velocity so vanilla physics carries it: it flies,
 * lands, and goes about its business. MULTISHOT fires a herd; the velocity/gravity
 * modifiers scale the throw. Restricted to spawnable living mobs (no bosses).
 *
 * <p>MOB is the whole delivery, so any AOE payload on the same shot is ignored —
 * it's the mob that's launched, not a burst.
 */
public final class MobModifier implements ParameterizedModifier {

    public static final String ID = "MOB";

    /** Bosses and oversized threats a player shouldn't be able to fling around. */
    private static final Set<EntityType> BLOCKED = EnumSet.of(
            EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN);

    /** The entity this instance launches, or null for the bare template. */
    private final EntityType type;

    public MobModifier() {
        this(null);
    }

    private MobModifier(EntityType type) {
        this.type = type;
    }

    @Override
    public Modifier withParameter(String param) {
        EntityType t = resolve(param);
        return t == null ? null : new MobModifier(t);
    }

    /** A spawnable, living, non-boss entity type by name, else null. */
    private static EntityType resolve(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        EntityType t;
        try {
            t = EntityType.valueOf(param.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!t.isSpawnable() || !t.isAlive() || BLOCKED.contains(t)) {
            return null;
        }
        return t;
    }

    @Override
    public String id() {
        return type == null ? ID : ID + ":" + type.name();
    }

    @Override
    public String displayName() {
        return type == null ? "Launch Mob" : "Launch " + Format.prettyName(type.name());
    }

    @Override
    public String description() {
        return type == null ? "hurls a live mob" : "hurls a live " + Format.prettyName(type.name()).toLowerCase(Locale.ROOT);
    }

    @Override
    public String detailedDescription() {
        return "Fires a living creature as the projectile — it flies with the shot's velocity, lands, and carries on. Add Multishot for a herd. (The mob is the whole payload.)";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        if (type != null) {
            builder.emitMob(type);
        }
    }

}
