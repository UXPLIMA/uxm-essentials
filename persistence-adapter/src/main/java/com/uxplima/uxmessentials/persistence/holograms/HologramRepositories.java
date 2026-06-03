package com.uxplima.uxmessentials.persistence.holograms;

import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the holograms context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link HologramRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath). The returned repository is the cached jOOQ adapter — write-through at the database, invalidate
 * in the Caffeine cache.
 */
@NullMarked
public final class HologramRepositories {

    private HologramRepositories() {}

    /** A cached jOOQ {@link HologramRepository} over the shared persistence DSL. */
    public static HologramRepository cached(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedHologramRepository(new JooqHologramRepository(persistence.dsl()));
    }
}
