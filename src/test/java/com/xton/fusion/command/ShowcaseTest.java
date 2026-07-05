package com.xton.fusion.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure structural checks on the UAT showcase roster. (That every modifier ID in
 * it resolves is asserted by the smoke self-test, which has the full registry.)
 */
class ShowcaseTest {

    @Test
    void namesAreDistinct() {
        // The display names are load-bearing: the manual checklist tells the
        // tester to grab items by these exact labels, so duplicates would be
        // ambiguous in the chest.
        List<String> names = Showcase.roster().stream().map(Showcase.Entry::name).toList();
        assertEquals(names.size(), names.stream().distinct().count(),
                "duplicate showcase names: " + names);
    }

    @Test
    void everyEntryCarriesModifiers() {
        for (Showcase.Entry entry : Showcase.roster()) {
            assertFalse(entry.modifiers().isEmpty(), entry.name() + " has no modifiers");
            assertFalse(entry.name().isBlank(), "an entry has a blank name");
        }
    }
}
