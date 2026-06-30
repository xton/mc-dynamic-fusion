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
import com.xton.fusion.modifier.impl.ChainModifier;
import com.xton.fusion.modifier.impl.ExpandModifier;
import com.xton.fusion.modifier.impl.NovaModifier;
import com.xton.fusion.modifier.impl.RepeatModifier;
import com.xton.fusion.util.BukkitTaskScheduler;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.util.Scheduler;
import com.xton.fusion.weapon.WeaponEventListener;
import com.xton.fusion.weapon.behaviors.SwingEffectBehavior;

/** Entry point — wires up the fusion loop and registered modifiers. */
public final class FusionPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int maxModifiers = getConfig().getInt("fusion.max-modifiers", 8);
        int maxGeneration = getConfig().getInt("fusion.max-generation", 5);
        long swingCooldownMs = getConfig().getLong("cooldown.swing-ms", 200);

        ModifierRegistry registry = new ModifierRegistry()
                .register(new NovaModifier(
                        getConfig().getDouble("nova.radius", 4.0),
                        getConfig().getDouble("nova.power", 1.4)))
                .register(new ExpandModifier(
                        getConfig().getDouble("expand.bonus-per-apply", 3.0)))
                .register(new ChainModifier(
                        getConfig().getInt("chain.count-per-apply", 3)))
                .register(new RepeatModifier(
                        getConfig().getInt("repeat.count-per-apply", 2)));

        LatentRegistry latent = loadLatentRegistry();

        FusionKeys keys = new FusionKeys(this);
        FusedItemReader reader = new FusedItemReader(keys);
        LoreGenerator lore = new LoreGenerator(registry);
        FusedItemFactory factory = new FusedItemFactory(keys, lore);
        FusionEngine engine = new FusionEngine(latent, reader, factory, maxModifiers, maxGeneration);

        Scheduler scheduler = new BukkitTaskScheduler(this);
        SwingEffectBehavior.Settings settings = new SwingEffectBehavior.Settings(
                getConfig().getDouble("effect.base-radius", 2.5),
                getConfig().getDouble("effect.base-power", 1.0),
                getConfig().getDouble("chain.range", 6.0),
                getConfig().getLong("repeat.delay-ticks", 4),
                getConfig().getBoolean("effect.affect-players", false));
        SwingEffectBehavior swingEffect = new SwingEffectBehavior(scheduler, settings);

        CooldownMap cooldown = new CooldownMap(swingCooldownMs);
        getServer().getPluginManager().registerEvents(
                new WeaponEventListener(reader, registry, swingEffect, cooldown), this);

        if (getCommand("fuse") != null) {
            getCommand("fuse").setExecutor(new FuseCommand(engine));
        } else {
            getLogger().warning("Command 'fuse' is missing from plugin.yml.");
        }

        getLogger().info("DynamicFusion enabled.");
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
