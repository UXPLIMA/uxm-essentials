package com.uxplima.uxmessentials.worlds.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;

/**
 * A typed read view over the worlds module's config subtree. The use cases consult this rather than
 * dotted config paths, so the config keys live in exactly one place and a reload simply reads a fresh
 * view from the swapped {@link ConfigStore}. Every getter carries a sensible default so a minimal config
 * still yields working behaviour.
 *
 * <p>The store is the module-scoped config (rooted at {@code modules.worlds}); the keys below are
 * relative to that root, matching the convention {@code TeleportSettings} follows.
 */
public final class WorldsSettings {

    private final ConfigStore config;

    public WorldsSettings(ConfigStore config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Whether the server's default world is shielded from {@code /worlds unload} and delete; default true. */
    public boolean protectDefaultWorld() {
        return config.getBoolean("protect-default-world", true);
    }

    /** Whether the enable-time reconcile adopts already-loaded worlds we do not yet manage; default true. */
    public boolean autoAdoptLoaded() {
        return config.getBoolean("auto-adopt-loaded", true);
    }

    /** Whether the enable-time reconcile auto-loads registered worlds flagged for it; default true. */
    public boolean autoLoadRegistered() {
        return config.getBoolean("auto-load-registered", true);
    }

    /**
     * The built-in flat generator's block bands, parsed from {@code generators.flat.layers} (each
     * {@code "<blockId> <height>"}, bottom→top); the "Classic Flat" plan when the key is absent or empty.
     */
    public FlatLayerPlan flatLayers() {
        return FlatLayerPlan.parse(config.getStringList("generators.flat.layers", List.of()));
    }

    /** The single biome the built-in void generator paints, {@code generators.void.biome}; default {@code plains}. */
    public BiomeId voidBiome() {
        return BiomeId.of(config.getString("generators.void.biome", "plains"));
    }

    /** The single biome the built-in flat generator paints, {@code generators.flat.biome}; default {@code plains}. */
    public BiomeId flatBiome() {
        return BiomeId.of(config.getString("generators.flat.biome", "plains"));
    }

    /** Whether a player who logs into a managed world they may no longer enter is redirected to the default world's spawn; default true. */
    public boolean redirectOnRestrictedJoin() {
        return config.getBoolean("access.redirect-on-restricted-join", true);
    }

    /** The largest square radius {@code /worlds pregen} will generate, {@code pregen.max-radius}; default 200, floor 1. */
    public int pregenMaxRadius() {
        return Math.max(1, config.getInt("pregen.max-radius", 200));
    }

    /** The cap on concurrent in-flight async chunk generations, {@code pregen.max-concurrent-chunks}; default 10, floor 1. */
    public int pregenMaxConcurrent() {
        return Math.max(1, config.getInt("pregen.max-concurrent-chunks", 10));
    }

    /** The pre-gen loop's tick period from {@code pregen.tick-period-ticks} (1 tick = 50ms); default one tick, floor one tick. */
    public java.time.Duration pregenTickPeriod() {
        return java.time.Duration.ofMillis(50L * Math.max(1, config.getInt("pregen.tick-period-ticks", 1)));
    }
}
