package com.uxplima.uxmessentials.persistence.teleport;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.teleport.application.port.PendingRelocations;
import org.jspecify.annotations.NullMarked;

/** Factory for the {@code /tpofflinehere} relocation store, so the bukkit-adapter never names a jOOQ type. */
@NullMarked
public final class PendingRelocationStores {

    private PendingRelocationStores() {}

    /** The jOOQ {@link PendingRelocations} over the shared persistence DSL. */
    public static PendingRelocations of(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPendingRelocations(persistence.dsl());
    }
}
