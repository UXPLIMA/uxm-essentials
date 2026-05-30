package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;

/**
 * Outbound port for spawn resolution: the default per-world spawn, operator-defined named spawns, and
 * the per-world spawn-mirror rules. The {@link com.uxplima.uxmessentials.teleport.application.ResolveSpawn}
 * use case folds a mirror (when present) through this directory so a mirrored {@code /spawn} reads the
 * live target-world spawn rather than a copied location.
 */
public interface SpawnDirectory {

    /** The default spawn for {@code world}, or empty when none is set. */
    Optional<Position> defaultSpawn(WorldRef world);

    /** A named spawn ({@code /spawn <name>}), or empty when no spawn answers to that name. */
    Optional<Position> namedSpawn(String name);

    /** The spawn-mirror rule for {@code world}, when {@code /spawn} there redirects elsewhere. */
    Optional<SpawnMirror> mirrorFor(WorldRef world);

    /** Set or replace the default spawn for {@code world}. */
    void setDefaultSpawn(WorldRef world, Position position);

    /** Set or replace the named spawn {@code name}. */
    void setNamedSpawn(String name, Position position);
}
