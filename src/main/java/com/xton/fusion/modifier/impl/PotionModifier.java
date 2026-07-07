package com.xton.fusion.modifier.impl;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ParameterizedModifier;
import com.xton.fusion.modifier.WeaponBuilder;
import com.xton.fusion.util.Format;

/**
 * Emitter: the Wand's magic. The effect is carried in the ID after a colon
 * ({@code POTION:POISON}, {@code POTION:REGENERATION}, ...) — fusing a
 * {@code LINGERING_POTION} onto a base contributes its own effect
 * automatically (see {@link com.xton.fusion.item.PotionLatent}). Right-clicking
 * a block with a fused wand casts a small lingering cloud of that effect there
 * (see {@code WandListener}); it does nothing on any other trigger path.
 */
public final class PotionModifier implements ParameterizedModifier {

    public static final String ID = "POTION";

    /** The effect this instance casts, or null for the bare (inert) template. */
    private final PotionEffectType type;

    public PotionModifier() {
        this(null);
    }

    private PotionModifier(PotionEffectType type) {
        this.type = type;
    }

    @Override
    public Modifier withParameter(String param) {
        PotionEffectType t = resolve(param);
        return t == null ? null : new PotionModifier(t);
    }

    /** A registered potion effect by its key name (e.g. {@code POISON}), else null. */
    private static PotionEffectType resolve(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.minecraft(param.trim().toLowerCase(Locale.ROOT));
        return Registry.EFFECT.get(key);
    }

    @Override
    public String id() {
        return type == null ? ID : ID + ":" + type.getKey().getKey().toUpperCase(Locale.ROOT);
    }

    @Override
    public String displayName() {
        return type == null ? "Potion" : "Potion of " + Format.prettyName(type.getKey().getKey());
    }

    @Override
    public String description() {
        return type == null ? "casts a lingering effect"
                : "casts a lingering " + Format.prettyName(type.getKey().getKey()).toLowerCase(Locale.ROOT) + " cloud";
    }

    @Override
    public String detailedDescription() {
        return "Makes this a Wand: right-click a block to leave a small lingering cloud of the fused potion's effect there, particles tinted to match. Fuse a Lingering Potion to load it.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        if (type != null) {
            builder.emitPotion(type);
        }
    }
}
