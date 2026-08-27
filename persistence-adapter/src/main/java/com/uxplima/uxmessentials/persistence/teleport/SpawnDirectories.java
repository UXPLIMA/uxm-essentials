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
 * <p>The production binding is wrapped by {@link CachedSpawnDirectory}; loaded worlds are warmed before listeners
 * are registered, keeping automatic join/respawn reads off the database.
 */
@NullMarked
public final class SpawnDirectories {

    private SpawnDirectories() {}

    /** A jOOQ {@link SpawnDirectory} over the shared persistence DSL. */
    public static SpawnDirectory jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqSpawnDirectory(persistence.dsl());
    }

    /** A write-through cached jOOQ directory, exposed concretely so bootstrap can warm loaded worlds. */
    public static CachedSpawnDirectory cachedConcrete(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        JooqSpawnDirectory durable = new JooqSpawnDirectory(persistence.dsl());
        return new CachedSpawnDirectory(durable, () -> java.util.Optional.of(durable.snapshot()));
    }
}
