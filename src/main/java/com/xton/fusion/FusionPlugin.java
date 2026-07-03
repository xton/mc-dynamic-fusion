package com.xton.fusion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
import com.xton.fusion.machine.MachineListener;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.WeaponBuilder;
import com.xton.fusion.modifier.impl.AmplifyModifier;
import com.xton.fusion.modifier.impl.ChainModifier;
import com.xton.fusion.modifier.impl.DamageModifier;
import com.xton.fusion.modifier.impl.ExpandModifier;
import com.xton.fusion.modifier.impl.InvertModifier;
import com.xton.fusion.modifier.impl.LifetimeModifier;
import com.xton.fusion.modifier.impl.MiningModifier;
import com.xton.fusion.modifier.impl.MultishotModifier;
import com.xton.fusion.modifier.impl.PersistModifier;
import com.xton.fusion.modifier.impl.PierceModifier;
import com.xton.fusion.modifier.impl.PushModifier;
import com.xton.fusion.modifier.impl.SpreadModifier;
import com.xton.fusion.projectile.AoeBurst;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.selftest.SelfTest;
import com.xton.fusion.util.BukkitTaskScheduler;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.util.Scheduler;
import com.xton.fusion.weapon.ProjectileListener;
import com.xton.fusion.weapon.ShedParticleTask;
import com.xton.fusion.weapon.WeaponEventListener;

/** Entry point — wires up the fusion loop and registered modifiers. */
public final class FusionPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int maxModifiers = getConfig().getInt("fusion.max-modifiers", 24);
        int fusionCost = getConfig().getInt("fusion.cost", 0);
        long swingCooldownMs = getConfig().getLong("cooldown.swing-ms", 200);
        boolean debug = getConfig().getBoolean("debug-logging", true);

        ModifierRegistry registry = new ModifierRegistry()
                // Emitters (concrete elements).
                .register(new PushModifier())
                .register(new DamageModifier())
                // AOE transforms (modify the nearest preceding emitter).
                .register(new ExpandModifier(
                        getConfig().getDouble("expand.factor-per-apply", 1.6)))
                .register(new AmplifyModifier(
                        getConfig().getDouble("amplify.factor-per-apply", 1.6)))
                .register(new ChainModifier(
                        getConfig().getInt("chain.count-per-apply", 2)))
                .register(new InvertModifier())
                .register(new PersistModifier(
                        getConfig().getInt("persist.ticks-per-apply", 60)))
                // Flight transforms (modify the projectile).
                .register(new MultishotModifier(
                        getConfig().getInt("multishot.count-per-apply", 2)))
                .register(new SpreadModifier(
                        getConfig().getDouble("spread.degrees-per-apply", 12.0)))
                .register(new PierceModifier())
                .register(new LifetimeModifier(
                        getConfig().getInt("lifetime.ticks-per-apply", 30)))
                .register(new MiningModifier(
                        getConfig().getInt("mining.lifetime-ticks", 6),
                        getConfig().getDouble("mining.speed", 2.5),
                        getConfig().getDouble("mining.max-hardness", 3.0)));

        LatentRegistry latent = loadLatentRegistry(registry);

        FusionKeys keys = new FusionKeys(this);
        FusedItemReader reader = new FusedItemReader(keys);
        LoreGenerator lore = new LoreGenerator(registry);
        FusedItemFactory factory = new FusedItemFactory(keys, lore);
        FusionEngine engine = new FusionEngine(latent, reader, factory, maxModifiers);

        Scheduler scheduler = new BukkitTaskScheduler(this);
        AoeBurst burst = new AoeBurst(scheduler, new AoeBurst.Settings(
                getConfig().getDouble("chain.range", 6.0),
                getConfig().getLong("persist.interval-ticks", 20),
                getConfig().getBoolean("effect.affect-players", false)));
        ProjectileLauncher launcher = new ProjectileLauncher(this, burst,
                new WeaponBuilder.Defaults(
                        getConfig().getDouble("projectile.base-speed", 1.6),
                        getConfig().getInt("projectile.base-lifetime-ticks", 30),
                        getConfig().getDouble("projectile.pierce-max-hardness", 3.0),
                        getConfig().getDouble("push.radius", 2.0),
                        getConfig().getDouble("push.power", 1.0),
                        getConfig().getDouble("damage.radius", 2.5),
                        getConfig().getDouble("damage.power", 4.0)));

        CooldownMap cooldown = new CooldownMap(swingCooldownMs);
        getServer().getPluginManager().registerEvents(
                new WeaponEventListener(reader, registry, launcher, cooldown), this);
        getServer().getPluginManager().registerEvents(
                new ProjectileListener(reader, registry, launcher), this);

        // Fusion Machine: placeable enchanting table, tagged via block-entity PDC
        // (no side file), opening the anvil-style fusion GUI on right-click.
        FusionMachineMenu menu = new FusionMachineMenu(engine, keys, fusionCost, getLogger(), debug);
        getServer().getPluginManager().registerEvents(new MachineListener(menu), this);

        // Ambient particle shedding (held fused weapons), toggleable.
        if (getConfig().getBoolean("effect.particle-shedding", true)) {
            scheduler.runRepeating(new ShedParticleTask(reader), 40,
                    getConfig().getLong("effect.shed-period-ticks", 4));
        }

        // Headless functional self-test (`/fusion test`), driving the real
        // projectile/burst code against a live world — used by the smoke boot.
        SelfTest selfTest = new SelfTest(scheduler, registry, launcher, burst, getLogger());

        if (getCommand("fusion") != null) {
            FusionCommand fusionCmd = new FusionCommand(menu, registry, factory, engine, fusionCost,
                    getLogger(), debug, selfTest);
            getCommand("fusion").setExecutor(fusionCmd);
            getCommand("fusion").setTabCompleter(fusionCmd);
        }

        getLogger().info("DynamicFusion enabled.");
    }

    /**
     * Load ingredient→modifier mappings with unambiguous ownership. Defaults live
     * only in the jar; the plugin never writes a user config (an
     * ambiguously-owned file is what let a stale mapping silently shadow renamed
     * modifiers). It refreshes a plugin-owned {@code latent_registry.example.yml}
     * with the current defaults, and if the user has created their own
     * {@code latent_registry.yml} that <em>fully replaces</em> the defaults (no
     * merge). Unknown modifier IDs are warned about rather than silently dropped.
     */
    private LatentRegistry loadLatentRegistry(ModifierRegistry registry) {
        writeExampleRegistry();

        File userFile = new File(getDataFolder(), "latent_registry.yml");
        ConfigurationSection section;
        if (userFile.exists()) {
            getLogger().info("Using your latent_registry.yml — it fully replaces the built-in "
                    + "defaults (no merge). Copy latent_registry.example.yml over it to pick up "
                    + "new defaults.");
            section = YamlConfiguration.loadConfiguration(userFile)
                    .getConfigurationSection("latent_modifiers");
        } else {
            section = bundledRegistrySection();
        }

        LatentRegistry latent = LatentRegistry.fromConfig(section, getLogger());
        warnUnknownModifiers(latent, registry);
        return latent;
    }

    /** Overwrite the plugin-owned example file with the current bundled defaults. */
    private void writeExampleRegistry() {
        try (InputStream in = getResource("latent_registry.yml")) {
            if (in == null) {
                return;
            }
            getDataFolder().mkdirs();
            String header = "# AUTO-GENERATED by DynamicFusion — do NOT edit.\n"
                    + "# Overwritten on every startup with the current defaults.\n"
                    + "# To customize, copy this file to latent_registry.yml (which the plugin\n"
                    + "# never touches) and edit that. Your latent_registry.yml fully REPLACES\n"
                    + "# these defaults — it is not merged — so copy the whole file, then tweak.\n\n";
            try (OutputStream out = new FileOutputStream(
                    new File(getDataFolder(), "latent_registry.example.yml"))) {
                out.write(header.getBytes(StandardCharsets.UTF_8));
                in.transferTo(out);
            }
        } catch (IOException e) {
            getLogger().warning("Could not write latent_registry.example.yml: " + e.getMessage());
        }
    }

    /** The bundled defaults' {@code latent_modifiers} section, straight from the jar. */
    private ConfigurationSection bundledRegistrySection() {
        try (InputStream in = getResource("latent_registry.yml")) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getConfigurationSection("latent_modifiers");
        } catch (IOException e) {
            getLogger().warning("Could not load bundled latent registry: " + e.getMessage());
            return null;
        }
    }

    /** Warn (don't silently drop) when an ingredient maps to a modifier that no longer exists. */
    private void warnUnknownModifiers(LatentRegistry latent, ModifierRegistry registry) {
        for (Map.Entry<org.bukkit.Material, List<String>> entry : latent.entries().entrySet()) {
            for (String id : entry.getValue()) {
                if (!registry.isKnown(id)) {
                    getLogger().warning("Ingredient " + entry.getKey() + " maps to unknown modifier '"
                            + id + "' — it will be ignored. Update latent_registry.yml"
                            + " (see latent_registry.example.yml for the current names).");
                }
            }
        }
    }
}
