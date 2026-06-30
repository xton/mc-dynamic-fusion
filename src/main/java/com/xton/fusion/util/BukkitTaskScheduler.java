package com.xton.fusion.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** {@link Scheduler} backed by the Bukkit main-thread scheduler. */
public final class BukkitTaskScheduler implements Scheduler {

    private final Plugin plugin;

    public BukkitTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            task.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
}
