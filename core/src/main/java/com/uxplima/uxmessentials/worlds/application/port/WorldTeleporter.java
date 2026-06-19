package com.uxplima.uxmessentials.worlds.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;

/**
 * Sends a player to a world-entry destination, delegating to the shared teleport context so the
 * world's entry teleport obeys the same warmup, cooldown, and move-cancels-warmup invariants as every
 * other teleport. The worlds context owns the destination and the {@link WorldTeleportCause}; the
 * mechanics belong to the teleport adapter behind this port.
 */
public interface WorldTeleporter {

    /**
     * Begins a teleport of {@code who} to {@code to} for the given {@code cause}.
     *
     * @return whether the teleport was accepted (a warmup was queued). The implementation owns the
     *     warmup, cooldown, and the player-facing notification on its own failures, so a {@code false}
     *     here means the request was rejected outright (already notified), not that the teleport failed
     *     silently.
     */
    boolean teleport(PlayerRef who, Position to, WorldTeleportCause cause);
}
