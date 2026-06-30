package com.xton.fusion.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Maps ingredient {@link Material}s to their latent modifier IDs.
 *
 * <p>Pure when constructed from a map (the map constructor needs no server),
 * which keeps lookup logic unit-testable.
 */
public final class LatentRegistry {

    private final Map<Material, List<String>> byMaterial;

    public LatentRegistry(Map<Material, List<String>> byMaterial) {
        Map<Material, List<String>> copy = new HashMap<>();
        byMaterial.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.byMaterial = Map.copyOf(copy);
    }

    public List<String> get(Material material) {
        return byMaterial.getOrDefault(material, List.of());
    }

    public boolean has(Material material) {
        return !get(material).isEmpty();
    }

    /**
     * Build a registry from a {@code latent_modifiers} configuration section,
     * skipping unknown material names rather than failing the whole load.
     */
    public static LatentRegistry fromConfig(ConfigurationSection section, java.util.logging.Logger log) {
        Map<Material, List<String>> map = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    log.warning("Unknown material in latent_registry.yml: " + key);
                    continue;
                }
                List<String> ids = new ArrayList<>(section.getStringList(key));
                map.put(material, ids);
            }
        }
        return new LatentRegistry(map);
    }
}
