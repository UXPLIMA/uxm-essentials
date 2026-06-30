package com.uxplima.uxmessentials.persistence.menu;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the menu engine's player-data persistence adapter, so the consuming bukkit-adapter wires a
 * {@link PlayerDataRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath). The
 * caching {@code PlayerDataStore} on the bukkit side wraps the returned repository.
 */
@NullMarked
public final class PlayerDataRepositories {

    private PlayerDataRepositories() {}

    /** A jOOQ {@link PlayerDataRepository} over the shared persistence DSL. */
    public static PlayerDataRepository jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPlayerDataRepository(persistence.dsl());
    }
}
