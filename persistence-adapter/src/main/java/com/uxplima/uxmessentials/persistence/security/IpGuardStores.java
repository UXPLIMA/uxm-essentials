package com.uxplima.uxmessentials.persistence.security;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.security.application.port.IpGuardStore;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the security context's IP/alt-guard adapter, so the consuming bukkit-adapter wires an
 * {@link IpGuardStore} from the {@link Persistence} handle without naming a jOOQ type (jOOQ is an
 * {@code implementation} dependency kept off the consumer's compile classpath). The returned store is stateless
 * over the shared DSL.
 */
@NullMarked
public final class IpGuardStores {

    private IpGuardStores() {}

    /** A jOOQ {@link IpGuardStore} over the shared persistence DSL. */
    public static IpGuardStore jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqIpGuardStore(persistence.dsl());
    }
}
