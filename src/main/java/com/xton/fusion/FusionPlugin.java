package com.xton.fusion;

import java.io.File;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.xton.fusion.command.FuseCommand;
import com.xton.fusion.command.FusionCommand;
import com.xton.fusion.fusion.FusionEngine;
import com.xton.fusion.item.FusedItemFactory;
import com.xton.fusion.item.FusedItemReader;
import com.xton.fusion.item.FusionKeys;
import com.xton.fusion.item.LatentRegistry;
import com.xton.fusion.item.LoreGenerator;
import com.xton.fusion.machine.FusionMachineMenu;
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

        // Fusion Machine (Phase 2): placeable block + GUI, persisted to machines.yml.
        MachineStore machines = new MachineStore(new File(getDataFolder(), "machines.yml"), getLogger());
        FusionMachineMenu menu = new FusionMachineMenu(engine, scheduler, keys, fusionCost);
        getServer().getPluginManager().registerEvents(new MachineListener(machines, menu), this);

        // Ambient particle shedding (Phase 4 polish), toggleable.
        if (getConfig().getBoolean("effect.particle-shedding", true)) {
            scheduler.runRepeating(new ShedParticleTask(reader), 40,
                    getConfig().getLong("effect.shed-period-ticks", 4));
        }

        if (getCommand("fuse") != null) {
            getCommand("fuse").setExecutor(new FuseCommand(engine, fusionCost));
        } else {
            getLogger().warning("Command 'fuse' is missing from plugin.yml.");
        }
        if (getCommand("fusion") != null) {
            getCommand("fusion").setExecutor(new FusionCommand(menu));
        }

        getLogger().info("DynamicFusion enabled (" + machines.size() + " machine(s) loaded).");
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
