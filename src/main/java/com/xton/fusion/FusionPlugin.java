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
import com.xton.fusion.machine.MachineGlowTask;
import com.xton.fusion.machine.MachineListener;
import com.xton.fusion.modifier.ModifierRegistry;
import com.xton.fusion.modifier.WeaponBuilder;
import com.xton.fusion.modifier.impl.AmplifyModifier;
import com.xton.fusion.modifier.impl.BounceModifier;
import com.xton.fusion.modifier.impl.ChainModifier;
import com.xton.fusion.modifier.impl.DamageModifier;
import com.xton.fusion.modifier.impl.DelayModifier;
import com.xton.fusion.modifier.impl.DepositModifier;
import com.xton.fusion.modifier.impl.DurationModifier;
import com.xton.fusion.modifier.impl.ExpandModifier;
import com.xton.fusion.modifier.impl.FireModifier;
import com.xton.fusion.modifier.impl.GlowModifier;
import com.xton.fusion.modifier.impl.GravityModifier;
import com.xton.fusion.modifier.impl.HealModifier;
import com.xton.fusion.modifier.impl.HomingModifier;
import com.xton.fusion.modifier.impl.IceModifier;
import com.xton.fusion.modifier.impl.InvertModifier;
import com.xton.fusion.modifier.impl.InvisibleModifier;
import com.xton.fusion.modifier.impl.LifetimeModifier;
import com.xton.fusion.modifier.impl.LiftModifier;
import com.xton.fusion.modifier.impl.MiningModifier;
import com.xton.fusion.modifier.impl.MobModifier;
import com.xton.fusion.modifier.impl.MultishotModifier;
import com.xton.fusion.modifier.impl.PersistModifier;
import com.xton.fusion.modifier.impl.PierceModifier;
import com.xton.fusion.modifier.impl.PullModifier;
import com.xton.fusion.modifier.impl.PushModifier;
import com.xton.fusion.modifier.impl.SpawnModifier;
import com.xton.fusion.modifier.impl.SpeedModifier;
import com.xton.fusion.modifier.impl.SpreadModifier;
import com.xton.fusion.modifier.impl.TeleportModifier;
import com.xton.fusion.modifier.impl.TrailModifier;
import com.xton.fusion.modifier.impl.TreasureModifier;
import com.xton.fusion.modifier.impl.VisibleModifier;
import com.xton.fusion.projectile.AoeBurst;
import com.xton.fusion.projectile.EnvironmentalAoe;
import com.xton.fusion.projectile.ProjectileLauncher;
import com.xton.fusion.weapon.GoldenBrush;
import com.xton.fusion.weapon.GoldenBrushListener;
import com.xton.fusion.selftest.SelfTest;
import com.xton.fusion.util.BukkitTaskScheduler;
import com.xton.fusion.util.CooldownMap;
import com.xton.fusion.util.Scheduler;
import com.xton.fusion.wearable.JetpackTask;
import com.xton.fusion.wearable.WornEffectTask;
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
                // Emitters (concrete elements) and their complements.
                .register(new PushModifier())
                .register(new PullModifier())
                .register(new DamageModifier())
                .register(new HealModifier())
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
                .register(new BounceModifier())
                .register(new HomingModifier())
                .register(new LifetimeModifier(
                        getConfig().getDouble("lifetime.range-per-apply", 12.0)))
                .register(new MiningModifier(
                        getConfig().getDouble("mining.base-radius", 1.0),
                        getConfig().getDouble("mining.base-hardness", 3.0),
                        getConfig().getDouble("mining.hardness-per-apply", 15.0)))
                // Delivery: launch a live mob as the projectile (parameterized).
                .register(new MobModifier())
                // Golden Brush: gold-scaled loot on brushing.
                .register(new TreasureModifier())
                // Worn effects (fused onto armor).
                .register(new GlowModifier())
                .register(new LiftModifier())
                // Environmental emitters (block/area effects along the flight).
                .register(new FireModifier())
                .register(new IceModifier())
                .register(new DepositModifier())
                // Structural: spawn children, delayed charge, trail, teleport.
                .register(new SpawnModifier())
                .register(new DelayModifier())
                .register(new TrailModifier())
                .register(new TeleportModifier())
                // Flight tuning: gravity/lob, trail visibility, absolute speed & lifetime.
                .register(new GravityModifier())
                .register(new VisibleModifier())
                .register(new InvisibleModifier())
                .register(new SpeedModifier())
                .register(new DurationModifier());

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
        EnvironmentalAoe.Settings envSettings = new EnvironmentalAoe.Settings(
                getConfig().getInt("fire.burn-ticks", 100),
                getConfig().getInt("ice.freeze-ticks", 140),
                getConfig().getDouble("environmental.max-radius", 8.0),
                getConfig().getDouble("environmental.max-hardness", 100.0));
        ProjectileLauncher launcher = new ProjectileLauncher(this, burst,
                new WeaponBuilder.Defaults(
                        getConfig().getDouble("projectile.base-speed", 1.6),
                        getConfig().getInt("projectile.base-lifetime-ticks", 30),
                        getConfig().getDouble("projectile.pierce-max-hardness", 3.0),
                        getConfig().getDouble("push.radius", 2.0),
                        getConfig().getDouble("push.power", 1.0),
                        getConfig().getDouble("damage.radius", 2.5),
                        getConfig().getDouble("damage.power", 4.0),
                        getConfig().getDouble("fire.base-radius", 1.5),
                        getConfig().getDouble("ice.base-radius", 1.5),
                        getConfig().getDouble("deposit.base-radius", 1.5)),
                envSettings,
                getConfig().getInt("spawn.max-generation", 2),
                getConfig().getInt("projectile.melee-lifetime-ticks", 1),
                getConfig().getDouble("projectile.melee-speed", 4.0));

        CooldownMap cooldown = new CooldownMap(swingCooldownMs);
        getServer().getPluginManager().registerEvents(
                new WeaponEventListener(reader, registry, launcher, cooldown), this);
        getServer().getPluginManager().registerEvents(
                new ProjectileListener(reader, registry, launcher), this);

        // Golden Brush: brushing a fused BRUSH with TREASURE (gold) rolls a loot table.
        GoldenBrush goldenBrush = new GoldenBrush(new GoldenBrush.Settings(
                getConfig().getDouble("golden-brush.proc-chance-base", 0.15),
                getConfig().getDouble("golden-brush.proc-chance-per-level", 0.1),
                getConfig().getDouble("golden-brush.proc-chance-cap", 0.75)));
        CooldownMap brushCooldown = new CooldownMap(getConfig().getLong("golden-brush.cooldown-ms", 250));
        getServer().getPluginManager().registerEvents(
                new GoldenBrushListener(reader, registry, launcher, goldenBrush, brushCooldown), this);

        // Fusion Machine: placeable enchanting table, tagged via block-entity PDC
        // (no side file), opening the anvil-style fusion GUI on right-click.
        FusionMachineMenu menu = new FusionMachineMenu(engine, keys, fusionCost, getLogger(), debug);
        getServer().getPluginManager().registerEvents(new MachineListener(menu), this);

        // Ambient particle shedding (held fused weapons), toggleable.
        if (getConfig().getBoolean("effect.particle-shedding", true)) {
            scheduler.runRepeating(new ShedParticleTask(reader), 40,
                    getConfig().getLong("effect.shed-period-ticks", 4));
        }

        // Ambient glow above placed Fusion Machines, so they stand out.
        if (getConfig().getBoolean("effect.machine-glow", true)) {
            scheduler.runRepeating(new MachineGlowTask(menu), 60,
                    getConfig().getLong("effect.machine-glow-period-ticks", 15));
        }

        // Worn-armor effects (GLOW makes the wearer glow), refreshed on a repeat.
        scheduler.runRepeating(new WornEffectTask(reader), 40,
                getConfig().getLong("worn.effect-period-ticks", 100));

        // Jetpack: a fused LIFT chestplate/elytra ramps a slow rise while airborne
        // and holding jump. Ticked every tick so the ramp feels smooth.
        scheduler.runRepeating(new JetpackTask(reader,
                getConfig().getDouble("worn.jetpack-thrust-per-tick", 0.03),
                getConfig().getDouble("worn.jetpack-max-velocity", 0.5)), 0, 1);

        // Headless functional self-test (`/fusion test`), driving the real
        // projectile/burst code against a live world — used by the smoke boot.
        SelfTest selfTest = new SelfTest(scheduler, registry, launcher, burst, getLogger());

        if (getCommand("fusion") != null) {
            FusionCommand fusionCmd = new FusionCommand(menu, registry, latent, factory, engine, fusionCost,
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
