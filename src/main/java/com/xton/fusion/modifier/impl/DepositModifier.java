package com.xton.fusion.modifier.impl;

import java.util.Locale;

import org.bukkit.Material;

import com.xton.fusion.modifier.Modifier;
import com.xton.fusion.modifier.ParameterizedModifier;
import com.xton.fusion.modifier.WeaponBuilder;

/**
 * Emitter: adds a DEPOSIT element — an environmental burst that fills the empty
 * cells in its radius with a block. The block to place is carried in the ID
 * after a colon ({@code DEPOSIT:DIRT}, {@code DEPOSIT:SAND}, ...), so one
 * template serves every material.
 *
 * <p>Because environmental kinds apply in stack order, {@code MINING PIERCE
 * DEPOSIT:DIRT} carves then backfills (a block-replacement bolt), while
 * {@code DEPOSIT:SAND} with TRAIL lays a suffocating trail. The registry holds
 * one bare {@code DEPOSIT} template; {@link #withParameter} mints the concrete,
 * material-bound instance.
 */
public final class DepositModifier implements ParameterizedModifier {

    public static final String ID = "DEPOSIT";

    /** The block this instance places, or null for the bare (inert) template. */
    private final Material material;

    /** The registry template — bare, matches {@code DEPOSIT} and mints instances. */
    public DepositModifier() {
        this(null);
    }

    private DepositModifier(Material material) {
        this.material = material;
    }

    @Override
    public Modifier withParameter(String param) {
        Material m = resolveMaterial(param);
        return m == null ? null : new DepositModifier(m);
    }

    /** Accept a placeable block material by name (e.g. {@code DIRT}), else null. */
    private static Material resolveMaterial(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        Material m = Material.matchMaterial(param.toUpperCase(Locale.ROOT));
        return m != null && m.isBlock() ? m : null;
    }

    @Override
    public String id() {
        return material == null ? ID : ID + ":" + material.name();
    }

    @Override
    public String displayName() {
        return material == null ? "Deposit" : "Deposit " + prettify(material);
    }

    @Override
    public String description() {
        return material == null ? "fills the air with a block" : "fills the air with " + prettify(material).toLowerCase(Locale.ROOT);
    }

    @Override
    public String detailedDescription() {
        return "Fills the empty space in a radius with a block. Combine with Mining (carve then backfill = block-replacement), or Trail with sand/gravel to bury what it flies over.";
    }

    @Override
    public Category category() {
        return Category.EMITTER;
    }

    @Override
    public void apply(WeaponBuilder builder) {
        if (material != null) {
            builder.emitDeposit(material);
        }
    }

    /** {@code POWDER_SNOW} → {@code "Powder Snow"} for readable lore. */
    private static String prettify(Material m) {
        String[] words = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
