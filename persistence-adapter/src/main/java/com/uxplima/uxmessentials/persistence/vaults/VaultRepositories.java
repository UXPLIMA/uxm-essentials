package com.uxplima.uxmessentials.persistence.vaults;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the vaults context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link VaultRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath). The
 * returned repository is the cached jOOQ adapter — write-through at the database, invalidate in the Caffeine
 * cache.
 */
@NullMarked
public final class VaultRepositories {

    private VaultRepositories() {}

    /** A cached jOOQ {@link VaultRepository} over the shared persistence DSL. */
    public static VaultRepository cached(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedVaultRepository(new JooqVaultRepository(persistence.dsl()));
    }
}
