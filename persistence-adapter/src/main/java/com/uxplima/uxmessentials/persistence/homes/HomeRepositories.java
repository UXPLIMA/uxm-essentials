package com.uxplima.uxmessentials.persistence.homes;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the homes context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link HomeRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath). The returned repository is the cached jOOQ adapter — write-through at the database,
 * invalidate in the Caffeine cache.
 */
@NullMarked
public final class HomeRepositories {

    private HomeRepositories() {}

    /** A cached jOOQ {@link HomeRepository} over the shared persistence DSL. */
    public static HomeRepository cached(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedHomeRepository(new JooqHomeRepository(persistence.dsl()));
    }
}
