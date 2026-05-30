package com.uxplima.uxmessentials.teleport.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the per-player teleport flags: the {@code /tptoggle} un-teleportable switch and the
 * {@code /tpblock} per-target block list. Both gate incoming {@code /tpa} requests — a request to a
 * toggled-off player is auto-denied, and a request from a blocked requester is silently refused. The
 * flags are session/PDC state, not persisted economy-grade data.
 */
public interface TeleportFlags {

    /** True when {@code who} has {@code /tptoggle} on and refuses all incoming requests. */
    boolean acceptsRequests(PlayerRef who);

    /** Flip {@code who}'s {@code /tptoggle} state, returning the new "accepts requests" value. */
    boolean toggleRequests(PlayerRef who);

    /** True when {@code target} has blocked {@code requester} via {@code /tpblock}. */
    boolean hasBlocked(PlayerRef target, PlayerRef requester);

    /** Block {@code requester}'s requests to {@code blocker}. */
    void block(PlayerRef blocker, PlayerRef requester);

    /** Unblock {@code requester}'s requests to {@code blocker}. */
    void unblock(PlayerRef blocker, PlayerRef requester);
}
