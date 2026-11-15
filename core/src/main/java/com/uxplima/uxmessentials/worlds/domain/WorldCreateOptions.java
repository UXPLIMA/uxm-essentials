package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * What an operator chose when asking for a new world. An empty field is a choice left to the configured
 * {@link WorldDefaults}, which is why this is not a {@link WorldSpec}: a spec has no way to say "not chosen".
 */
public record WorldCreateOptions(
        Optional<WorldEnvironment> environment,
        Optional<WorldGenType> worldType,
        Optional<Long> seed,
        Optional<GeneratorRef> generator) {

    public WorldCreateOptions {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(worldType, "worldType");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(generator, "generator");
    }

    /** Nothing chosen: every field comes from the defaults, the seed is random and generation is vanilla. */
    public static WorldCreateOptions none() {
        return new WorldCreateOptions(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
