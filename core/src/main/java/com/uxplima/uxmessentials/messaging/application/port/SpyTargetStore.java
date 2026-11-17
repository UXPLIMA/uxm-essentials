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

    /**
     * Start watching {@code added} and stop watching {@code removed} for {@code spy}. Only these rows change, so a
     * player another backend added meanwhile stays watched.
     */
    void change(UUID spy, Set<UUID> added, Set<UUID> removed);
}
