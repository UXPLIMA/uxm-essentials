package com.uxplima.uxmessentials.persistence.worlds;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;

/**
 * Factory for the worlds context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link WorldRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath). The returned repository is the cached jOOQ adapter scoped to one backend: write-through at the
 * database, applied to the in-memory snapshot.
 */
public final class WorldRepositories {

    private WorldRepositories() {}

    /** A cached jOOQ {@link WorldRepository} for the backend named {@code server}. */
    public static WorldRepository cached(Persistence persistence, String server) {
        return cachedConcrete(persistence, server);
    }

    /**
     * The cached jOOQ {@link WorldRepository} as its concrete decorator type, so the wiring can reload the snapshot
     * the commands read when a peer reports a change. Same backing as {@link #cached}.
     */
    public static CachedWorldRepository cachedConcrete(Persistence persistence, String server) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedWorldRepository(new JooqWorldRepository(persistence.dsl(), server), server);
    }
}
