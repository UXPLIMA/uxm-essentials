package com.uxplima.uxmessentials.teleport.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.RequestId;

/**
 * A pending teleport request was accepted by its target; the mover's warmup may now begin.
 *
 * @param requestId the resolved request's identity
 * @param requester the player who issued the request
 * @param target the player who accepted
 */
public record TeleportRequestAccepted(RequestId requestId, PlayerRef requester, PlayerRef target)
        implements TeleportEvent {

    public TeleportRequestAccepted {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
    }
}
