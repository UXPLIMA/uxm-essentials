package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * What a world created through the plugin starts with where the operator chose nothing: its environment, generation
 * preset and structure toggle, and the per-world settings it is saved with.
 */
public record WorldDefaults(
        WorldEnvironment environment, WorldGenType worldType, boolean generateStructures, WorldSettings settings) {

    public WorldDefaults {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(worldType, "worldType");
        Objects.requireNonNull(settings, "settings");
    }

    /** The values a world gets when the config says nothing: a normal world with structures and catalog settings. */
    public static WorldDefaults vanilla() {
        return new WorldDefaults(WorldEnvironment.NORMAL, WorldGenType.NORMAL, true, WorldSettings.defaults());
    }

    /** The spec a world is created from: each option the operator chose, these defaults for the rest. */
    public WorldSpec specFor(WorldCreateOptions options) {
        Objects.requireNonNull(options, "options");
        return new WorldSpec(
                options.environment().orElse(environment),
                options.worldType().orElse(worldType),
                options.seed(),
                options.generator(),
                generateStructures,
                Optional.empty());
    }
}
