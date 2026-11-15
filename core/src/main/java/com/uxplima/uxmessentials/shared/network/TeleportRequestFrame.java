package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A teleport request a player on {@link #originServer()} sent to a player on {@link #targetServer()}. Every peer
 * receives it and only the target's backend acts on it: it judges the request by its own toggle, block list and
 * vanish state, holds it until the target answers or it expires, and replies with {@link TeleportAnswerFrame}s.
 *
 * @param originServer the requester's backend
 * @param requestId the request both backends agree on; a repeat with the same id is the same request
 * @param requesterUuid the player who asked
 * @param requesterName the requester's name, for the target's lines
 * @param targetUuid the player asked
 * @param targetName the target's name
 * @param targetServer the backend the target is on, the only one that acts on the frame
 * @param direction which side moves on accept, as the teleport context's direction name (text, so this shared frame
 *     never depends on a feature context's enum)
 * @param seeLevel the requester's vanish see level, so the target's backend can hide a vanished target from them
 * @param ttlMillis how long the request stays open, so both backends expire it alike
 */
public record TeleportRequestFrame(
        String originServer,
        UUID requestId,
        UUID requesterUuid,
        String requesterName,
        UUID targetUuid,
        String targetName,
        String targetServer,
        String direction,
        int seeLevel,
        long ttlMillis)
        implements NetworkMessage {

    public TeleportRequestFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requesterUuid, "requesterUuid");
        Objects.requireNonNull(requesterName, "requesterName");
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(direction, "direction");
    }

    @Override
    public MessageType type() {
        return MessageType.TELEPORT_REQUEST;
    }
}
