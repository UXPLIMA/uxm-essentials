package com.uxplima.uxmessentials.worlds.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Looks up a single managed world for {@code /world info}. */
public final class WorldInfo {

    private final WorldRepository repository;

    public WorldInfo(WorldRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Optional<ManagedWorld> find(WorldName name) {
        return repository.find(Objects.requireNonNull(name, "name"));
    }
}
