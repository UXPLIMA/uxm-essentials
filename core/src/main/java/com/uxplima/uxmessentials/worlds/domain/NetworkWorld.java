package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A managed world as the network sees it: the world and the backend that runs it. Two backends may each run a world
 * of the same name, so the pair is what tells them apart.
 *
 * @param server the network name of the backend that runs the world, or {@value #UNSCOPED} while no backend has
 *     claimed it yet
 * @param world the world itself
 */
public record NetworkWorld(String server, ManagedWorld world) {

    /**
     * The server of a world that existed before worlds recorded one and that no backend has claimed. Never saved by a
     * running backend, so a backend configured under this name is refused the claim.
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

    /** True when {@code localServer} runs the world. */
    public boolean livesOn(String localServer) {
        Objects.requireNonNull(localServer, "localServer");
        return !unclaimed() && server.equals(localServer);
    }

    public WorldName name() {
        return world.name();
    }
}
