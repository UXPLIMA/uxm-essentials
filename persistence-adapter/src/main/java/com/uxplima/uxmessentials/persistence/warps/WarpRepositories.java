package com.uxplima.uxmessentials.persistence.warps;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the warps context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link WarpRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath). The returned repository is the cached jOOQ adapter — write-through at the database,
 * invalidate in the Caffeine cache.
 */
@NullMarked
public final class WarpRepositories {

    private WarpRepositories() {}

    /** A cached jOOQ {@link WarpRepository} over the shared persistence DSL. */
    public static WarpRepository cached(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedWarpRepository(new JooqWarpRepository(persistence.dsl()));
    }
}
