package com.uxplima.uxmessentials.persistence.skin;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the skin context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link SkinRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is a {@code compileOnly} dependency of this module, kept off the consumer's compile classpath). The
 * returned repository is the plain jOOQ adapter over the shared persistence DSL: a skin is read once per join and
 * written only when a player changes it, so no cache decorator is warranted.
 */
@NullMarked
public final class SkinRepositories {

    private SkinRepositories() {}

    /** A jOOQ {@link SkinRepository} over the shared persistence DSL. */
    public static SkinRepository jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqSkinRepository(persistence.dsl());
    }
}
