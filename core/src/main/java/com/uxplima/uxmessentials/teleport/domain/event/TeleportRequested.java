package com.uxplima.uxmessentials.teleport.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;

/**
 * A teleport request was issued and is now pending the target's decision.
 *
 * @param requestId the new request's identity
 * @param requester the player who issued the request
 * @param target the player asked to accept or deny
 * @param direction whether the requester moves to the target or the reverse
 * @param expiresAt the instant the request's TTL elapses if unresolved
 */
public record TeleportRequested(
        RequestId requestId, PlayerRef requester, PlayerRef target, RequestDirection direction, Instant expiresAt)
        implements TeleportEvent {

    public TeleportRequested {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
