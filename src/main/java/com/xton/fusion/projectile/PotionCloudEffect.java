package com.xton.fusion.projectile;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.xton.fusion.modifier.AoeSpec;

/**
 * Payload effect that casts a POTION emitter's lingering cloud (see
 * {@link PotionCloud}) at the termination point — so any weapon, not just the
 * Wand's own point-and-cast, can carry POTION and leave a cloud wherever its
 * shot lands. Uses the plain config duration rather than the spec's own
 * {@code lifetimeTicks}: that field already governs this weapon's own
 * flight/arm timing (DURATION on a bouncing grenade, say, controls how long it
 * stays armed, not how long the cloud it leaves behind should linger).
 */
public final class PotionCloudEffect implements PayloadEffect {

    private final AoeSpec spec;
    private final PotionCloud.Settings settings;

    public PotionCloudEffect(AoeSpec spec, PotionCloud.Settings settings) {
        this.spec = spec;
        this.settings = settings;
    }

    @Override
    public void deliver(World world, Location where, Player caster) {
        PotionCloud.cast(where.clone().add(0, 0.5, 0), spec.potionEffectType(), spec.radius(),
                settings.cloudDurationTicks(), settings);
    }
}
