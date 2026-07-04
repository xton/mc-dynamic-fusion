package com.xton.fusion.modifier.impl;

/** Tiny formatting helpers shared by the parameterized modifiers. */
final class Format {

    private Format() {
    }

    /**
     * Render a number canonically for a modifier ID / display: a whole value
     * drops its decimals ({@code 2.0} → {@code "2"}), otherwise trailing zeros are
     * trimmed ({@code 1.50} → {@code "1.5"}). Keeps IDs stable across PDC
     * round-trips.
     */
    static String number(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        String s = Double.toString(value);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }
}
