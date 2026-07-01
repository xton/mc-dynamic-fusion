package com.xton.fusion.modifier.impl;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ModifierContext;

/**
 * Inverts the effect: instead of shoving entities outward, the burst pulls them
 * inward toward its centre (an implosion). Toggles — two INVERTs cancel.
 *
 * <p>Backfire by design: an inverted NOVA on your own swing yanks mobs onto you
 * (a mob magnet). Faithful to what you built, not a malfunction.
 *
 * <p>Pure: only toggles a flag.
 */
public final class InvertModifier implements Modifier {

    public static final String ID = "INVERT";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Invert";
    }

    @Override
    public String description() {
        return "pulls inward instead of out";
    }

    @Override
    public String detailedDescription() {
        return "Turns the shove into an implosion that drags entities toward the centre. Careful — that centre might be you.";
    }

    @Override
    public ModifierContext apply(ModifierContext ctx) {
        // INVERT flips a burst inward, so it opts one in.
        return ctx.enableBurst().toggleInverted();
    }
}
