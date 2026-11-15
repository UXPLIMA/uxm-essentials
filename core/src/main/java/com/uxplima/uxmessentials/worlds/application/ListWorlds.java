package com.uxplima.uxmessentials.worlds.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Lists the managed worlds for {@code /world list}: this backend's, then, while worlds of other servers can be reached,
 * the other backends' by server, then the worlds no backend has claimed yet. Only this backend's worlds can say
 * whether they are loaded.
 *
 * <p>An unclaimed row that some backend already holds a copy of is a template the start-up claim keeps for other
 * backends with the same world, not a world waiting for a home, so it is left out.
 */
public final class ListWorlds {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final String localScope;
    private final boolean showRemote;

    /**
     * Lists over {@code repository}, with the loaded state read from {@code engine}.
     *
     * @param localScope the server this backend keeps its rows under, and lists its worlds under:
     *     {@value NetworkWorld#UNSCOPED} when it records none, the value the database and {@code /worlds setserver} use
     * @param showRemote whether the worlds other backends run are listed, which is only useful while they can be
     *     reached
     */
    public ListWorlds(WorldRepository repository, WorldEngine engine, String localScope, boolean showRemote) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.localScope = Objects.requireNonNull(localScope, "localScope");
        this.showRemote = showRemote;
    }

    public List<WorldListEntry> all() {
        List<NetworkWorld> network = repository.network();
        Set<WorldName> held = network.stream()
                .filter(world -> !world.unclaimed())
                .map(NetworkWorld::name)
                .collect(Collectors.toSet());
        return network.stream()
                .filter(world -> world.livesOn(localScope) || !world.unclaimed() || !held.contains(world.name()))
                .map(this::entryOf)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(WorldListEntry::rank)
                        .thenComparing(entry -> entry.server().orElse(""))
                        .thenComparing(entry -> entry.name().value()))
                .toList();
    }

    private Optional<WorldListEntry> entryOf(NetworkWorld world) {
        WorldEnvironment environment = world.world().spec().environment();
        if (world.livesOn(localScope)) {
            return Optional.of(new WorldListEntry(
                    world.name(), engine.isLoaded(world.name()), environment, Optional.of(localScope), true));
        }
        if (world.unclaimed() || showRemote) {
            return Optional.of(new WorldListEntry(world.name(), false, environment, world.knownServer(), false));
        }
        return Optional.empty();
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
