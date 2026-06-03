package com.uxplima.uxmessentials.persistence.teleport;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the teleport context's durable spawn directory, so the consuming bukkit-adapter wires a
 * {@link SpawnDirectory} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath).
 *
 * <p>Left uncached: a {@code /spawn} read is a single keyed lookup on a primary-key index, already cheap, and
 * the bukkit-adapter decorates this store with the vanilla-world last-resort the embedded backend cannot know.
 */
@NullMarked
public final class SpawnDirectories {

    private SpawnDirectories() {}

    /** A jOOQ {@link SpawnDirectory} over the shared persistence DSL. */
    public static SpawnDirectory jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqSpawnDirectory(persistence.dsl());
    }
}
