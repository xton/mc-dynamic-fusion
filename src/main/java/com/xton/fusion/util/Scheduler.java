package com.xton.fusion.util;

/**
 * Thin seam over task scheduling so timing-based behaviour (REPEAT, and later
 * DELAYED/PERSIST) can be driven by a fake in tests instead of the Bukkit
 * scheduler.
 */
public interface Scheduler {

    /** Run {@code task} after {@code delayTicks} (run immediately if <= 0). */
    void runLater(Runnable task, long delayTicks);

    /** Run {@code task} repeatedly every {@code periodTicks} after an initial delay. */
    void runRepeating(Runnable task, long initialDelayTicks, long periodTicks);
}
