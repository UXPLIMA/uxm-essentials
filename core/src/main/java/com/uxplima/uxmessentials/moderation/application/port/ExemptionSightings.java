package com.uxplima.uxmessentials.moderation.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * What each backend last saw of a player's punishment exemption, kept in the shared database. A backend records the
 * live answer while the player is on it; another backend reads it for a player who is not online, so an exemption
 * that only one backend can see (its own operator list, a permission store the others do not share) still counts.
 *
 * <p>Every method does I/O and runs off the tick.
 */
public interface ExemptionSightings {

    /**
     * {@code server} saw {@code player} holding the exemption ({@code held}) or not at {@code at}. Replaces that
     * server's earlier sighting, unless that one is newer.
     */
    void record(UUID player, String server, boolean held, Instant at);

    /** Whether any backend's latest sighting of {@code player} holds the exemption. */
    boolean heldAnywhere(UUID player);
}
