package com.uxplima.uxmessentials.persistence.security;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.security.application.port.AttemptStore;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the security context's device-trust adapter, so the consuming bukkit-adapter wires a {@link TrustStore}
 * from the {@link Persistence} handle without naming a jOOQ type (jOOQ is an {@code implementation} dependency kept off
 * the consumer's compile classpath). The returned store is stateless over the shared DSL.
 */
@NullMarked
public final class TrustStores {

    private TrustStores() {}

    /** A jOOQ {@link TrustStore} over the shared persistence DSL. */
    public static TrustStore jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqTrustStore(persistence.dsl());
    }

    /** The jOOQ {@link AttemptStore}: the wrong proofs and lockouts every backend on the database shares. */
    public static AttemptStore attempts(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqAttemptStore(persistence.dsl());
    }

    /** The jOOQ {@link VerifiedSessions}: the proofs a player may carry to another backend on the database. */
    public static VerifiedSessions sessions(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqVerifiedSessions(persistence.dsl());
    }
}
