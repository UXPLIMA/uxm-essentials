package com.uxplima.uxmessentials.vanish.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.vanish.domain.StoredVanish;

/**
 * The durable vanish state of each player. A port because it is a platform boundary: the database every backend on the
 * network shares, which is what lets a vanish follow a player to another server and through a restart. Both calls
 * block on I/O, so callers run them off the tick.
 */
public interface VanishRecords {

    /** The kept state of {@code player}, or empty when they never had one. */
    Optional<StoredVanish> find(UUID player);

    /**
     * Keep {@code state} for {@code player} unless the kept row was written later, so two writes that land out of order
     * settle on the newer one.
     */
    void save(UUID player, StoredVanish state);
}
