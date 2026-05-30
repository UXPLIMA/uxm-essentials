package com.uxplima.uxmessentials.teleport.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.RequestId;

/**
 * A pending teleport request reached its TTL without being resolved; both parties are notified. An
 * expired request never burns the requester's cooldown under the {@code accept} or {@code teleport}
 * start phases.
 *
 * @param requestId the expired request's identity
 * @param requester the player whose request lapsed
 * @param target the player who never decided it
 */
public record TeleportRequestExpired(RequestId requestId, PlayerRef requester, PlayerRef target)
        implements TeleportEvent {

    public TeleportRequestExpired {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
    }
}
