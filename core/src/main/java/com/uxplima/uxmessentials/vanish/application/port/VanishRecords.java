package com.uxplima.uxmessentials.vanish.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.vanish.domain.StoredVanish;

/**
 * The last written vanish state of each player. A port because it is a platform boundary: the database every backend
 * on the network shares, which is what lets a vanish follow a player through a server switch, and a persisting
 * player through a restart. Every call blocks on I/O, so callers run them off the tick.
 */
public interface VanishRecords {

    /** The kept state of {@code player}, or empty when they never had one. */
    Optional<StoredVanish> find(UUID player);

    /**
     * Keep {@code state} for {@code player} unless the kept row was written later, so two writes that land out of order
     * settle on the newer one.
     */
    void save(UUID player, StoredVanish state);

    /**
     * Record that {@code player} left a backend at {@code at} while vanished. Nothing happens when the kept row says
     * they are visible: a backend they reached since has already decided, and a late departure must not bring the
     * vanish back.
     */
    void markDeparted(UUID player, Instant at);
}
