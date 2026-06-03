package com.uxplima.uxmessentials.persistence.homes;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.MainHomePreference;
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

    /**
     * The cached jOOQ {@link HomeRepository} as its concrete decorator type, so the wiring can hand the
     * cross-server bus a per-owner invalidation hook on the same cache the commands read — a remote
     * {@code /sethome} drops exactly that owner's cached set. Same backing as {@link #cached}; this overload
     * exposes the decorator only so the invalidation seam can reach it.
     */
    public static CachedHomeRepository cachedConcrete(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedHomeRepository(new JooqHomeRepository(persistence.dsl()));
    }

    /**
     * A jOOQ {@link MainHomePreference} over the shared persistence DSL — the per-player main-home choice
     * plain {@code /home} consults. Left uncached: the row is a single keyed lookup read off the
     * {@code /home} hot path, already cheap, and a tiny one-row choice gains nothing from a cache decorator.
     */
    public static MainHomePreference mainHomePreference(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqMainHomePreference(persistence.dsl());
    }
}
