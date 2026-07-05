package com.xton.fusion.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure: string formatting only. */
class FormatTest {

    @Test
    void wholeNumbersDropDecimals() {
        assertEquals("2", Format.number(2.0));
        assertEquals("0", Format.number(0.0));
        assertEquals("-3", Format.number(-3.0));
    }

    @Test
    void fractionsTrimTrailingZeros() {
        assertEquals("0.8", Format.number(0.8));
        assertEquals("1.5", Format.number(1.50));
        assertEquals("0.25", Format.number(0.25));
    }

    @Test
    void numberRoundTripsThroughAParameterId() {
        // A modifier ID like SPEED:0.8 must re-parse to the same value it printed.
        double value = 0.8;
        assertEquals(value, Double.parseDouble(Format.number(value)), 1.0e-12);
    }

    @Test
    void prettyNameTitleCasesEnumNames() {
        assertEquals("Nether Star", Format.prettyName("NETHER_STAR"));
        assertEquals("Cow", Format.prettyName("COW"));
        assertEquals("Powder Snow Bucket", Format.prettyName("POWDER_SNOW_BUCKET"));
        assertEquals("", Format.prettyName(""));
    }
}
