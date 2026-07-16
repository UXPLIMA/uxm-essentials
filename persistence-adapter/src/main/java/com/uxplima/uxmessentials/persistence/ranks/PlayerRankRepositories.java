package com.uxplima.uxmessentials.persistence.ranks;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the ranks context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link PlayerRankRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath).
 * The returned repository is the jOOQ adapter over the shared DSL, stamping each write with the system clock.
 */
@NullMarked
public final class PlayerRankRepositories {

    private PlayerRankRepositories() {}

    /** A jOOQ {@link PlayerRankRepository} over the shared persistence DSL. */
    public static PlayerRankRepository jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPlayerRankRepository(persistence.dsl(), Clock.systemUTC());
    }
}
