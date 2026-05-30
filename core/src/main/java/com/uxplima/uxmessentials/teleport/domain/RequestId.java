package com.uxplima.uxmessentials.teleport.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identity of one {@link TeleportRequest}, independent of the requester and target uuids so a
 * player may have several outstanding requests in the multi-queue mode without their ids colliding.
 *
 * @param value the request's unique identifier
 */
public record RequestId(UUID value) {

    public RequestId {
        Objects.requireNonNull(value, "value");
    }

    /** Mint a fresh, random request id. */
    public static RequestId random() {
        return new RequestId(UUID.randomUUID());
    }
}
