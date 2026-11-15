package com.uxplima.uxmessentials.worlds.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Lists every managed world of the network with the backend that runs it, for {@code /world list}. Only this
 * backend's worlds can say whether they are loaded; another backend's world reads as not loaded here.
 */
public final class ListWorlds {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final String localServer;

    public ListWorlds(WorldRepository repository, WorldEngine engine, String localServer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.localServer = Objects.requireNonNull(localServer, "localServer");
    }

    /** This backend's worlds first, then the other backends' by server, then the unclaimed ones. */
    public List<WorldListEntry> all() {
        return repository.network().stream()
                .map(this::entryOf)
                .sorted(Comparator.comparingInt(WorldListEntry::rank)
                        .thenComparing(entry -> entry.server().orElse(""))
                        .thenComparing(entry -> entry.name().value()))
                .toList();
    }

    private WorldListEntry entryOf(NetworkWorld world) {
        boolean here = world.livesOn(localServer);
        return new WorldListEntry(
                world.name(),
                here && engine.isLoaded(world.name()),
                world.world().spec().environment(),
                world.knownServer(),
                here);
    }

    /**
     * One row of the world list.
     *
     * @param name the world's name
     * @param loaded whether this backend has it loaded; false for another backend's world
     * @param environment what the world was made as
     * @param server the backend that runs it, empty while no backend has claimed it
     * @param here true when this backend runs it
     */
    public record WorldListEntry(
            WorldName name, boolean loaded, WorldEnvironment environment, Optional<String> server, boolean here) {

        public WorldListEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(server, "server");
        }

        /** True while no backend has claimed the world. */
        public boolean unclaimed() {
            return server.isEmpty();
        }

        private int rank() {
            if (here) {
                return 0;
            }
            return unclaimed() ? 2 : 1;
        }
    }
}
