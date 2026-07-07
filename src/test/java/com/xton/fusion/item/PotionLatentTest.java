package com.xton.fusion.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A Lingering Potion's latent isn't a static Material mapping — it depends on
 * which potion it is — so this exercises the item-meta read directly.
 */
class PotionLatentTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack lingeringPotion(PotionType type) {
        ItemStack item = new ItemStack(Material.LINGERING_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    @Test
    void carriesTheBasePotionsEffect() {
        List<String> ids = PotionLatent.extra(lingeringPotion(PotionType.POISON));
        assertEquals(List.of("POTION:POISON"), ids);
    }

    @Test
    void usesTheEffectKeyNotThePotionTypeName() {
        // HARMING the PotionType maps to the INSTANT_DAMAGE effect.
        List<String> ids = PotionLatent.extra(lingeringPotion(PotionType.HARMING));
        assertEquals(List.of("POTION:INSTANT_DAMAGE"), ids);
    }

    @Test
    void noEffectPotionsContributeNothing() {
        assertTrue(PotionLatent.extra(lingeringPotion(PotionType.WATER)).isEmpty());
        assertTrue(PotionLatent.extra(lingeringPotion(PotionType.AWKWARD)).isEmpty());
    }

    @Test
    void ignoresNonLingeringItems() {
        ItemStack splash = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) splash.getItemMeta();
        meta.setBasePotionType(PotionType.POISON);
        splash.setItemMeta(meta);
        assertTrue(PotionLatent.extra(splash).isEmpty());
        assertTrue(PotionLatent.extra(new ItemStack(Material.STICK)).isEmpty());
    }
}
