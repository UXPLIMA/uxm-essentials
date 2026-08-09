package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmPresence;

/**
 * Who is away from their keyboard.
 *
 * <p>Everything here answers straight away rather than through a future: presence is held in memory for the
 * players who are online and is never written down, so there is nothing to wait for. That also means a player who
 * is offline has no presence at all, which is why {@link #of(UUID)} can be empty for a player who plainly exists.
 */
public interface UxmPresenceQuery {

    /** What is known about this player right now, or empty when they are not online. */
    Optional<UxmPresence> of(UUID playerId);

    /** Whether this player is away. False for a player who is offline, since nobody is at that keyboard either. */
    boolean isAfk(UUID playerId);

    /** Every player who is away right now, in no particular order. */
    List<UxmPresence> afk();
}
