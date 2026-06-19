package com.uxplima.uxmessentials.worlds.application;

import java.time.Duration;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The pure idle-unload decision: only an empty world idle at-or-past a positive threshold unloads, and a
 * non-positive threshold disables unloading entirely.
 */
@NullMarked
public final class AutoUnloadPolicy {

    private AutoUnloadPolicy() {}

    /** Whether a world with {@code playerCount} players, idle for {@code idleFor}, should unload under {@code threshold}. */
    public static boolean shouldUnload(int playerCount, Duration idleFor, Duration threshold) {
        Objects.requireNonNull(idleFor, "idleFor");
        Objects.requireNonNull(threshold, "threshold");
        return threshold.compareTo(Duration.ZERO) > 0 && playerCount == 0 && idleFor.compareTo(threshold) >= 0;
    }
}
