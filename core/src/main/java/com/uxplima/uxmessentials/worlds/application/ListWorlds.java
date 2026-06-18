package com.uxplima.uxmessentials.worlds.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Lists every managed world with its current loaded state, for {@code /world list}. */
public final class ListWorlds {

    private final WorldRepository repository;
    private final WorldEngine engine;

    public ListWorlds(WorldRepository repository, WorldEngine engine) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public List<WorldListEntry> all() {
        return repository.all().stream()
                .map(w -> new WorldListEntry(
                        w.name(), engine.isLoaded(w.name()), w.spec().environment()))
                .toList();
    }

    /** One row of the world list. */
    public record WorldListEntry(WorldName name, boolean loaded, WorldEnvironment environment) {}
}
