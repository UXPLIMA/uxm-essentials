package com.uxplima.uxmessentials.persistence.lookup;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the name-index persistence adapter, so the consuming bukkit-adapter wires a
 * {@link PlayerNameRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath).
 */
@NullMarked
public final class PlayerNameRepositories {

    private PlayerNameRepositories() {}

    /** A jOOQ {@link PlayerNameRepository} over the shared persistence DSL. */
    public static PlayerNameRepository jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPlayerNameRepository(persistence.dsl());
    }
}
