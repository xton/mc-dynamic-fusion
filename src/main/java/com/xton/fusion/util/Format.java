package com.xton.fusion.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Small, pure formatting helpers shared across the plugin. */
public final class Format {

    private Format() {
    }

    /**
     * Render a number canonically for a modifier ID / display: a whole value
     * drops its decimals ({@code 2.0} → {@code "2"}), otherwise trailing zeros are
     * trimmed ({@code 1.50} → {@code "1.5"}). Keeps IDs stable across PDC
     * round-trips.
     */
    public static String number(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        String s = Double.toString(value);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    /**
     * Title-case an enum-style name for display: {@code NETHER_STAR} →
     * {@code "Nether Star"}. Locale-stable (ROOT), so IDs render the same on any
     * server locale.
     */
    public static String prettyName(String enumName) {
        return Arrays.stream(enumName.toLowerCase(Locale.ROOT).split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
