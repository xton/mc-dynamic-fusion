package com.xton.fusion.fusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.xton.fusion.item.FusedItemFactory;
import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.item.FusionKeys;
import com.xton.fusion.item.LatentRegistry;
import com.xton.fusion.item.Lineage;
import com.xton.fusion.item.LoreGenerator;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.PushModifier;

/**
 * Needs MockBukkit because it round-trips ItemStack meta / PDC, which requires
 * {@code Bukkit.getItemFactory()}.
 */
class FusionEngineTest {

    private FusedItemReader reader;
    private FusedItemFactory factory;
    private LatentRegistry latent;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();

        FusionKeys keys = new FusionKeys(plugin);
        ModifierRegistry registry = new ModifierRegistry().register(new PushModifier());
        reader = new FusedItemReader(keys);
        factory = new FusedItemFactory(keys, new LoreGenerator(registry));
        latent = new LatentRegistry(Map.of(Material.NETHER_STAR, List.of("PUSH")));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private FusionEngine engine(int maxModifiers) {
        return new FusionEngine(latent, reader, factory, maxModifiers);
    }

    @Test
    void firstFusionProducesPushSword() {
        FusionResult result = engine(8)
                .fuse(new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.NETHER_STAR));

        assertTrue(result.success(), result.message());
        ItemStack out = result.output();
        assertEquals(Material.DIAMOND_SWORD, out.getType());
        assertTrue(reader.isFused(out));
        assertEquals(List.of("PUSH"), reader.readModifierIds(out));
    }

    @Test
    void ingredientWithoutLatentMagicIsRefused() {
        FusionResult result = engine(8)
                .fuse(new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.DIRT));

        assertFalse(result.success());
    }

    @Test
    void fusedIngredientContributesItsStackPlusLatent() {
        FusionEngine engine = engine(8);
        // A fused sword (NOVA) used as the ingredient should hand over its stack.
        ItemStack fusedSword = engine
                .fuse(new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.NETHER_STAR)).output();

        ItemStack out = engine.fuse(new ItemStack(Material.DIAMOND_AXE), fusedSword).output();
        // Diamond Sword has no latent modifiers, so the axe gains exactly the
        // ingredient's fused stack.
        assertEquals(List.of("PUSH"), reader.readModifierIds(out));
    }

    @Test
    void emptyHandsAreRefused() {
        assertFalse(engine(8).fuse(null, new ItemStack(Material.NETHER_STAR)).success());
        assertFalse(engine(8).fuse(new ItemStack(Material.DIAMOND_SWORD), null).success());
    }

    @Test
    void duplicatesStackOnRefusion() {
        FusionEngine engine = engine(8);
        ItemStack once = engine.fuse(new ItemStack(Material.DIAMOND_SWORD),
                new ItemStack(Material.NETHER_STAR)).output();
        ItemStack twice = engine.fuse(once, new ItemStack(Material.NETHER_STAR)).output();

        assertEquals(List.of("PUSH", "PUSH"), reader.readModifierIds(twice));
    }

    @Test
    void lineageAccumulatesAndCollapses() {
        FusionEngine engine = engine(24);
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        for (int i = 0; i < 3; i++) {
            item = engine.fuse(item, new ItemStack(Material.NETHER_STAR)).output();
        }
        assertEquals("Diamond Sword + 3× Nether Star", Lineage.render(reader.fusedFrom(item)));
    }

    @Test
    void modifierCapTruncatesTail() {
        FusionEngine engine = engine(2);
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        for (int i = 0; i < 3; i++) {
            item = engine.fuse(item, new ItemStack(Material.NETHER_STAR)).output();
        }
        assertEquals(2, reader.readModifierIds(item).size());
    }
}
