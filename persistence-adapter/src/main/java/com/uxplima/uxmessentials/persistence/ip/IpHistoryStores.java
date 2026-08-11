package com.uxplima.uxmessentials.persistence.ip;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the kernel IP-history adapter, so the consuming bukkit-adapter wires an {@link IpHistoryStore} from
 * the {@link Persistence} handle without naming a jOOQ type (jOOQ is an {@code implementation} dependency kept off
 * the consumer's compile classpath). The returned store is stateless over the shared DSL.
 */
@NullMarked
public final class IpHistoryStores {

    private IpHistoryStores() {}

    /** A jOOQ {@link IpHistoryStore} over the shared persistence DSL. */
    public static IpHistoryStore jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqIpHistoryStore(persistence.dsl());
    }
}
