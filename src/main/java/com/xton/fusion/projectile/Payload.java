package com.xton.fusion.projectile;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * What a projectile delivers where it terminates: an ordered list of
 * {@link PayloadEffect}s. May be empty — a projectile with an empty payload
 * (e.g. a pure mining ray or kinetic lance) simply stops, delivering nothing.
 */
public final class Payload {

    private static final Payload EMPTY = new Payload(List.of());

    private final List<PayloadEffect> effects;

    public Payload(List<PayloadEffect> effects) {
        this.effects = List.copyOf(effects);
    }

    public static Payload empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    /** Deliver every effect at the termination point, in order. */
    public void detonate(World world, Location where, Player caster) {
        for (PayloadEffect effect : effects) {
            effect.deliver(world, where, caster);
        }
    }
}
