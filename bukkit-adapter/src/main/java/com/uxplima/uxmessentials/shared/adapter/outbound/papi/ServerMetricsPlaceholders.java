package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.OptionalInt;

/**
 * Read seam the expansion queries for the {@code server_*} placeholders — the server-wide metrics that need no
 * requesting player (online count, slots, version, process uptime, TPS, heap usage, per-world population). Unlike
 * every other seam this one is not owned by a feature context: it reads Bukkit and JVM globals, so it is always
 * present, wired once in bootstrap regardless of which modules are enabled.
 *
 * <p>It exposes the raw primitives only; the {@link PlaceholderResolver} owns the rendering (the dash defaults,
 * the megabyte rounding, the green/yellow/red TPS colouring). Keeping the seam value-typed lets the resolver test
 * exercise the {@code server_*} family with a plain fake, no live server.
 */
public interface ServerMetricsPlaceholders {

    /** Players currently connected. */
    int onlinePlayers();

    /** The configured slot count. */
    int maxPlayers();

    /** The running Minecraft version, e.g. {@code 1.21.11}. */
    String minecraftVersion();

    /** How long the plugin has been enabled — captured from the enable timestamp, not JVM start. */
    Duration uptime();

    /**
     * The 1-minute, 5-minute and 15-minute tick rates, in that order. Always three elements; a server that has
     * not yet measured a window reports its idle ceiling for it.
     */
    double[] tps();

    /** Used heap, in megabytes (committed total minus free). */
    long ramUsedMb();

    /** Maximum heap the JVM may grow to, in megabytes. */
    long ramMaxMb();

    /** Free heap within the currently committed total, in megabytes. */
    long ramFreeMb();

    /** Players in the named world, or empty when no world carries that name. */
    OptionalInt worldPlayers(String world);
}
