package com.uxplima.uxmessentials.teleport.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.RequestId;

/**
 * A requester withdrew their own outstanding teleport request via {@code /tpcancel}.
 *
 * @param requestId the withdrawn request's identity
 * @param requester the player who cancelled their request
 * @param target the player who would have decided it
 */
public record TeleportRequestCancelled(RequestId requestId, PlayerRef requester, PlayerRef target)
        implements TeleportEvent {

    public TeleportRequestCancelled {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
    }
}
