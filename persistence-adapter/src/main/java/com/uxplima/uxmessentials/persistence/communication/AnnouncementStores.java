package com.uxplima.uxmessentials.persistence.communication;

import java.util.Objects;

import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the communication context's announcement-store adapter, so the consuming bukkit-adapter wires an
 * {@link AnnouncementStore} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath).
 */
@NullMarked
public final class AnnouncementStores {

    private AnnouncementStores() {}

    /** A jOOQ {@link AnnouncementStore} over the shared persistence DSL. */
    public static AnnouncementStore jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqAnnouncementStore(persistence.dsl());
    }
}
