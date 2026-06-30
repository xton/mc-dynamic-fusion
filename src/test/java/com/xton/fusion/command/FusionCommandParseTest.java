package com.xton.fusion.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.ChainModifier;
import com.xton.fusion.modifier.impl.NovaModifier;

/** Pure: argument → known-modifier-id parsing, no server needed. */
class FusionCommandParseTest {

    private ModifierRegistry registry() {
        return new ModifierRegistry()
                .register(new NovaModifier())
                .register(new ChainModifier(3));
    }

    @Test
    void keepsKnownIdsUpcasedAndSkipsUnknown() {
        ModifierRegistry registry = registry();
        // args: give <player> <base> nova MYSTERY chain
        String[] args = {"give", "Steve", "DIAMOND_SWORD", "nova", "MYSTERY", "chain"};
        List<String> ids = FusionCommand.knownIds(registry, args, 3);

        assertEquals(List.of("NOVA", "CHAIN"), ids);
    }

    @Test
    void preservesOrderAndDuplicates() {
        ModifierRegistry registry = registry();
        String[] args = {"give", "Steve", "BOW", "nova", "nova", "chain"};
        List<String> ids = FusionCommand.knownIds(registry, args, 3);

        assertEquals(List.of("NOVA", "NOVA", "CHAIN"), ids);
    }
}
