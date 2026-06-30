package com.xton.fusion;

import java.io.File;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.xton.fusion.command.FuseCommand;
import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.item.FusedItemFactory;
import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.item.FusionKeys;
import com.xton.fusion.item.LatentRegistry;
import com.xton.fusion.item.LoreGenerator;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.NovaModifier;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.weapon.WeaponEventListener;
import com.xton.fusion.weapon.behaviors.NovaBehavior;

/** Phase Zero entry point — wires up the single Nova Sword fusion loop. */
public final class FusionPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        double novaRadius = getConfig().getDouble("nova.radius", 4.0);
        double novaPower = getConfig().getDouble("nova.power", 1.4);
        int maxModifiers = getConfig().getInt("fusion.max-modifiers", 8);
        int maxGeneration = getConfig().getInt("fusion.max-generation", 5);
        long novaCooldownMs = getConfig().getLong("cooldown.nova-ms", 200);

        ModifierRegistry registry = new ModifierRegistry()
                .register(new NovaModifier(novaRadius, novaPower));

        LatentRegistry latent = loadLatentRegistry();

        FusionKeys keys = new FusionKeys(this);
        FusedItemReader reader = new FusedItemReader(keys);
        LoreGenerator lore = new LoreGenerator(registry);
        FusedItemFactory factory = new FusedItemFactory(keys, lore);
        FusionEngine engine = new FusionEngine(latent, reader, factory, maxModifiers, maxGeneration);

        NovaBehavior nova = new NovaBehavior();
        CooldownMap novaCooldown = new CooldownMap(novaCooldownMs);
        getServer().getPluginManager().registerEvents(
                new WeaponEventListener(reader, registry, nova, novaCooldown), this);

        if (getCommand("fuse") != null) {
            getCommand("fuse").setExecutor(new FuseCommand(engine));
        } else {
            getLogger().warning("Command 'fuse' is missing from plugin.yml.");
        }

        getLogger().info("DynamicFusion (Phase Zero) enabled.");
    }

    private LatentRegistry loadLatentRegistry() {
        File file = new File(getDataFolder(), "latent_registry.yml");
        if (!file.exists()) {
            saveResource("latent_registry.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("latent_modifiers");
        return LatentRegistry.fromConfig(section, getLogger());
    }
}
