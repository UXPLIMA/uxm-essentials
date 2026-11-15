package com.uxplima.uxmessentials.vanish.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.vanish.domain.StoredVanish;

/**
 * The last written vanish state of each player. A port because it is a platform boundary: the database every backend
 * on the network shares, which is what lets a vanish follow a player through a server switch, and a persisting player
 * through a restart. Every call blocks on I/O, so callers run them off the tick.
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
     * Record that the vanished {@code player} left {@code server} at {@code at}. Nothing happens unless the kept row
     * still says they are vanished on that server: a backend they reached since has already claimed it, and a late
     * departure must neither bring a cleared vanish back nor mark the player gone from where they are now.
     */
    void markDeparted(UUID player, String server, Instant at);

    /**
     * Record that the vanished {@code player} is now on {@code server}, seen there at {@code at}; nothing happens unless
     * the row says vanished.
     */
    void markArrived(UUID player, String server, Instant at);

    /** Confirm that the vanished {@code players} are still on {@code server} at {@code at}, for rows it holds. */
    void touch(Collection<UUID> players, String server, Instant at);

    /**
     * Record a departure at {@code at} for every vanished player whose row {@code server} holds without a departure,
     * except {@code stillHere}: they cannot be on a backend that is starting or stopping.
     */
    void releaseHeld(String server, Collection<UUID> stillHere, Instant at);
}
