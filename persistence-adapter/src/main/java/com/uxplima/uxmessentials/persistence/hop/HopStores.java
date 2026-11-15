package com.uxplima.uxmessentials.persistence.hop;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHopStore;
import org.jspecify.annotations.NullMarked;

/** Factory for the shared cross-server hop's store, so the bukkit-adapter never names a jOOQ type. */
@NullMarked
public final class HopStores {

    private HopStores() {}

    /**
     * The jOOQ {@link PendingHopStore}: one row per player over the network-shared pending-hop table, the handoff
     * between a hop's origin and target backends, for every kind of hop.
     */
    public static PendingHopStore pending(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPendingHopStore(persistence.dsl());
    }
}
