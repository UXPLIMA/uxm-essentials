package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A teleport request one player has open on another, still waiting to be accepted or denied.
 *
 * <p>Which of the two moves depends on the direction rather than on who asked, which is the whole difference
 * between {@code /tpa} and {@code /tpahere}; {@link #moverId()} answers it without the caller having to work it
 * out. A request that has already been resolved or has run out of time is gone rather than reported as stale.
 *
 * @param requesterId the player who asked
 * @param requesterName the name they are known by
 * @param targetId the player who was asked
 * @param targetName the name they are known by
 * @param direction which of them would move
 * @param expiresAt when the request lapses on its own
 */
public record UxmTeleportRequest(
        UUID requesterId,
        String requesterName,
        UUID targetId,
        String targetName,
        UxmTeleportRequestDirection direction,
        Instant expiresAt) {

    public UxmTeleportRequest {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(requesterName, "requesterName");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** The player who would actually teleport if this were accepted. */
    public UUID moverId() {
        return direction == UxmTeleportRequestDirection.TO_TARGET ? requesterId : targetId;
    }

    /** The player the mover would land next to. */
    public UUID anchorId() {
        return direction == UxmTeleportRequestDirection.TO_TARGET ? targetId : requesterId;
    }
}
