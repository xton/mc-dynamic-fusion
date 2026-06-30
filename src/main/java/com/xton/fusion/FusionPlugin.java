package com.xton.fusion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.xton.fusion.command.FusionCommand;
import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.item.FusedItemFactory;
import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.item.FusionKeys;
import com.xton.fusion.item.LatentRegistry;
import com.xton.fusion.item.LoreGenerator;
import com.xton.fusion.machine.FusionMachineMenu;
import com.xton.fusion.machine.MachineGlowTask;
import com.xton.fusion.machine.MachineListener;
import com.xton.fusion.machine.MachineStore;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.impl.ChainModifier;
import com.xton.fusion.modifier.impl.DelayedModifier;
import com.xton.fusion.modifier.impl.ExpandModifier;
import com.xton.fusion.modifier.impl.InvertModifier;
import com.xton.fusion.modifier.impl.MiningModifier;
import com.xton.fusion.modifier.impl.NovaModifier;
import com.xton.fusion.modifier.impl.PersistModifier;
import com.xton.fusion.modifier.impl.RepeatModifier;
import com.xton.fusion.util.BukkitTaskScheduler;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.util.Scheduler;
import com.xton.fusion.weapon.ProjectileListener;
import com.xton.fusion.weapon.ShedParticleTask;
import com.xton.fusion.weapon.WeaponEventListener;
import com.xton.fusion.weapon.behaviors.MiningRayBehavior;
import com.xton.fusion.weapon.behaviors.SwingEffectBehavior;

/** Entry point — wires up the fusion loop and registered modifiers. */
public final class FusionPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int maxModifiers = getConfig().getInt("fusion.max-modifiers", 8);
        int maxGeneration = getConfig().getInt("fusion.max-generation", 5);
        int fusionCost = getConfig().getInt("fusion.cost", 0);
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
                        getConfig().getInt("repeat.count-per-apply", 2)))
                .register(new DelayedModifier(
                        getConfig().getInt("delayed.ticks-per-apply", 30)))
                .register(new MiningModifier())
                .register(new InvertModifier())
                .register(new PersistModifier(
                        getConfig().getInt("persist.ticks-per-apply", 60)));

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
                getConfig().getLong("persist.interval-ticks", 20),
                getConfig().getBoolean("effect.affect-players", false));
        SwingEffectBehavior swingEffect = new SwingEffectBehavior(scheduler, settings);

        MiningRayBehavior miningRay = new MiningRayBehavior(new MiningRayBehavior.Settings(
                getConfig().getDouble("mining.range", 4.0),
                getConfig().getDouble("mining.arc-degrees", 45.0),
                getConfig().getDouble("mining.step-degrees", 15.0),
                getConfig().getDouble("mining.max-hardness", 3.0)));

        CooldownMap cooldown = new CooldownMap(swingCooldownMs);
        getServer().getPluginManager().registerEvents(
                new WeaponEventListener(reader, registry, swingEffect, miningRay, cooldown), this);
        getServer().getPluginManager().registerEvents(
                new ProjectileListener(reader, registry, swingEffect, keys), this);

        // Fusion Machine: placeable anvil + vanilla anvil GUI, persisted to machines.yml.
        MachineStore machines = new MachineStore(new File(getDataFolder(), "machines.yml"), getLogger());
        FusionMachineMenu menu = new FusionMachineMenu(engine, keys, fusionCost);
        getServer().getPluginManager().registerEvents(new MachineListener(machines, menu), this);

        // Ambient particle shedding (held fused weapons), toggleable.
        if (getConfig().getBoolean("effect.particle-shedding", true)) {
            scheduler.runRepeating(new ShedParticleTask(reader), 40,
                    getConfig().getLong("effect.shed-period-ticks", 4));
        }
        // Ambient glow above placed machines so they're easy to spot.
        if (getConfig().getBoolean("effect.machine-glow", true)) {
            scheduler.runRepeating(new MachineGlowTask(machines), 20,
                    getConfig().getLong("effect.machine-glow-period-ticks", 10));
        }

        if (getCommand("fusion") != null) {
            FusionCommand fusionCmd = new FusionCommand(menu, registry, factory, engine, fusionCost);
            getCommand("fusion").setExecutor(fusionCmd);
            getCommand("fusion").setTabCompleter(fusionCmd);
        }

        getLogger().info("DynamicFusion enabled (" + machines.size() + " machine(s) loaded).");
    }

    private LatentRegistry loadLatentRegistry() {
        File file = new File(getDataFolder(), "latent_registry.yml");
        if (!file.exists()) {
            saveResource("latent_registry.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // Merge the bundled defaults so ingredients added in newer plugin
        // versions appear even when the on-disk file predates them (it is only
        // written when absent). The user's own entries win on conflict.
        try (InputStream in = getResource("latent_registry.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                yaml.setDefaults(defaults);
                yaml.options().copyDefaults(true);
            }
        } catch (IOException e) {
            getLogger().warning("Could not load default latent registry: " + e.getMessage());
        }
        ConfigurationSection section = yaml.getConfigurationSection("latent_modifiers");
        return LatentRegistry.fromConfig(section, getLogger());
    }
}
