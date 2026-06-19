package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * {@link WorldsPlaceholders} over the worlds context's {@link WorldRepository} and {@link WorldEngine}. The
 * managed count reads the same Caffeine-cached registry the {@code /world list} use cases hold, so it matches
 * what the listing shows; the loaded count and default-world reads go through the engine's live world handles.
 *
 * <p>The repository is the cached jOOQ adapter, so {@link WorldRepository#all()} is a cheap cache read; taking
 * its size, the loaded-name set size, and the default-world player count are all safe on the placeholder path.
 */
@NullMarked
public final class RepositoryWorldsPlaceholders implements WorldsPlaceholders {

    private final WorldRepository repository;
    private final WorldEngine engine;

    public RepositoryWorldsPlaceholders(WorldRepository repository, WorldEngine engine) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public int managedCount() {
        return repository.all().size();
    }

    @Override
    public int loadedCount() {
        return engine.loadedWorldNames().size();
    }

    @Override
    public Optional<String> defaultWorld() {
        return engine.defaultWorldName().map(WorldName::value);
    }

    @Override
    public int defaultWorldPlayers() {
        return engine.defaultWorldName().map(engine::playerCount).orElse(0);
    }
}
