package com.xton.fusion.machine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Tracks which block locations are Fusion Machines, persisted to a flat
 * {@code machines.yml} so placed machines survive restarts.
 */
public final class MachineStore {

    private final File file;
    private final Logger log;
    private final Set<String> keys = new HashSet<>();

    public MachineStore(File file, Logger log) {
        this.file = file;
        this.log = log;
        load();
    }

    static String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";"
                + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public boolean contains(Location loc) {
        return keys.contains(key(loc));
    }

    public void add(Location loc) {
        if (keys.add(key(loc))) {
            save();
        }
    }

    public void remove(Location loc) {
        if (keys.remove(key(loc))) {
            save();
        }
    }

    public int size() {
        return keys.size();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        keys.addAll(yaml.getStringList("machines"));
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("machines", new ArrayList<>(keys));
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            log.warning("Failed to save machines.yml: " + e.getMessage());
        }
    }
}
