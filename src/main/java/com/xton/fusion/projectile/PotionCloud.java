package com.xton.fusion.projectile;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Plants a stable, non-shrinking {@link AreaEffectCloud} loaded with a POTION
 * emitter's effect — the vanilla lingering-potion cloud entity, tuned to hold
 * steady for its whole life instead of vanilla's shrink-to-vanish. Shared by
 * every way POTION can be delivered: the Wand's point-and-cast
 * ({@code WandListener}) and any weapon's own terminus ({@link PotionCloudEffect}) —
 * POTION always produces the same cloud, whatever delivered it.
 */
public final class PotionCloud {

    /** Tunables resolved from config. */
    public record Settings(int cloudDurationTicks, int effectDurationTicks, int amplifier) {
    }

    private PotionCloud() {
    }

    /**
     * Cast {@code type}'s cloud at {@code where}, sized to {@code radius} and
     * lasting {@code durationTicks} (the caller's call: the Wand seeds this with
     * its own long default, DURATION-overridable; a flying weapon's own
     * DURATION already governs its flight/arm timing, so it passes the plain
     * config default instead — see {@link PotionCloudEffect}).
     */
    public static void cast(Location where, PotionEffectType type, double radius, int durationTicks, Settings settings) {
        AreaEffectCloud cloud = where.getWorld().spawn(where, AreaEffectCloud.class);
        cloud.setRadius((float) radius);
        cloud.setRadiusOnUse(0f);
        cloud.setRadiusPerTick(0f); // hold steady for its whole life instead of vanilla's shrink-to-vanish
        cloud.setDuration(durationTicks);
        cloud.setParticle(Particle.ENTITY_EFFECT, type.getColor());
        cloud.setColor(type.getColor());
        cloud.addCustomEffect(new PotionEffect(type, settings.effectDurationTicks(), settings.amplifier()), true);
        where.getWorld().playSound(where, Sound.ENTITY_SPLASH_POTION_BREAK, 0.7f, 1.0f);
    }
}
