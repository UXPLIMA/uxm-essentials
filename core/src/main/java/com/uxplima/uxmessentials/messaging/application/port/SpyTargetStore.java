package com.uxplima.uxmessentials.messaging.application.port;

import java.util.Set;
import java.util.UUID;

/**
 * Where the players a staff member watches with {@code /socialspy <player>} are kept between backends: the shared
 * database. A port because it is a storage boundary. Both calls do I/O and run off the tick.
 */
public interface SpyTargetStore {

    /** The players {@code spy} watches; empty when they watch nobody. */
    Set<UUID> load(UUID spy);

    /** Store {@code targets} as the whole set of players {@code spy} watches. */
    void save(UUID spy, Set<UUID> targets);
}
