package com.xton.fusion.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.ChainModifier;
import com.xton.fusion.modifier.impl.PushModifier;

/** Pure: argument → known-modifier-id parsing, no server needed. */
class FusionCommandParseTest {

    private ModifierRegistry registry() {
        return new ModifierRegistry()
                .register(new PushModifier())
                .register(new ChainModifier(2));
    }

    @Test
    void keepsKnownIdsUpcasedAndSkipsUnknown() {
        ModifierRegistry registry = registry();
        // args: give <player> <base> push MYSTERY chain
        String[] args = {"give", "Steve", "DIAMOND_SWORD", "push", "MYSTERY", "chain"};
        List<String> ids = FusionCommand.knownIds(registry, args, 3);

        assertEquals(List.of("PUSH", "CHAIN"), ids);
    }

    @Test
    void preservesOrderAndDuplicates() {
        ModifierRegistry registry = registry();
        String[] args = {"give", "Steve", "BOW", "push", "push", "chain"};
        List<String> ids = FusionCommand.knownIds(registry, args, 3);

        assertEquals(List.of("PUSH", "PUSH", "CHAIN"), ids);
    }
}
