package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A managed world as the network sees it: the world and the backend that runs it. Two backends may each run a world
 * of the same name, so the pair is what tells them apart.
 *
 * @param server the network name of the backend that runs the world, or {@value #UNSCOPED} while no backend has
 *     claimed it yet, which is also where a backend off a network or under the shipped default name keeps its own
 * @param world the world itself
 */
public record NetworkWorld(String server, ManagedWorld world) {

    /**
     * The server of a world no networked backend has claimed: a world from before worlds recorded one, or a world of a
     * backend that records no server (off a network, or under the shipped default name). Such a backend reads and
     * writes these rows as its own, so a standalone server behaves as before, and a backend that joins a network
     * under a real name claims them.
     */
    public static final String UNSCOPED = "unscoped";

    public NetworkWorld {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(world, "world");
    }

    /** The backend that runs the world, empty while it is unclaimed. */
    public Optional<String> knownServer() {
        return unclaimed() ? Optional.empty() : Optional.of(server);
    }

    /** True while no backend has claimed the world. */
    public boolean unclaimed() {
        return server.isBlank() || server.equals(UNSCOPED);
    }

    /** True when the backend whose rows are kept under {@code localScope} runs the world. */
    public boolean livesOn(String localScope) {
        Objects.requireNonNull(localScope, "localScope");
        return server.equals(localScope);
    }

    public WorldName name() {
        return world.name();
    }
}
