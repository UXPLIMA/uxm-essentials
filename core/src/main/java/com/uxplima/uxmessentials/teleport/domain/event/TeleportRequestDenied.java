package com.uxplima.uxmessentials.teleport.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.RequestId;

/**
 * A pending teleport request was denied by its target. A denied request never burns the requester's
 * cooldown under the {@code accept} or {@code teleport} start phases.
 *
 * @param requestId the resolved request's identity
 * @param requester the player whose request was denied
 * @param target the player who denied it
 */
public record TeleportRequestDenied(RequestId requestId, PlayerRef requester, PlayerRef target)
        implements TeleportEvent {

    public TeleportRequestDenied {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
    }
}
