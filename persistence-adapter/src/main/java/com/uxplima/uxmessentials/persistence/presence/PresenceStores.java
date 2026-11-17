package com.uxplima.uxmessentials.persistence.presence;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.presence.application.port.NickRecords;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the presence context's persistence adapters, so the bukkit-adapter wires them from the
 * {@link Persistence} handle it already holds without naming a jOOQ type.
 */
@NullMarked
public final class PresenceStores {

    private PresenceStores() {}

    /** The players' nicks over the shared persistence DSL. */
    public static NickRecords nicks(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqNickRecords(persistence.dsl());
    }
}
