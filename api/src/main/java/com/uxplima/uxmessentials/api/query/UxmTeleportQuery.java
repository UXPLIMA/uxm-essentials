package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmBackPoint;
import com.uxplima.uxmessentials.api.view.UxmTeleportRequest;

/**
 * The teleport requests in flight, and where {@code /back} would take somebody.
 *
 * <p>Requests live in memory and are dropped the moment they are accepted, denied or run out of time, so
 * everything here answers straight away and never reports one that no longer stands.
 */
public interface UxmTeleportQuery {

    /** The requests waiting on this player, oldest first, which is the order they would resolve in. */
    List<UxmTeleportRequest> pendingFor(UUID playerId);

    /** The request this player has open on somebody else, or empty when they have none. */
    Optional<UxmTeleportRequest> outgoingFrom(UUID playerId);

    /**
     * Where {@code /back} would return this player to, or empty when nothing has been recorded for them. The
     * plugin records a return point before it moves somebody, so this is where they were, not where they are.
     */
    Optional<UxmBackPoint> backPoint(UUID playerId);
}
