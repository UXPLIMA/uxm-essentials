package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A staff teleport of a player who is on another backend: the staff member on {@link #originServer()} asks the
 * subject's backend to move its player to the destination player, wherever that one is. Only
 * {@link #subjectServer()} acts on it, and it reports back with a {@link TeleportAnswerFrame}.
 *
 * @param originServer the staff member's backend
 * @param requestId the teleport both backends agree on; a repeat with the same id is the same teleport
 * @param actorUuid the staff member, or the console's fixed id
 * @param actorName the staff member's name
 * @param subjectUuid the player to move
 * @param subjectName the subject's name
 * @param destinationUuid the player to move them to
 * @param destinationName the destination player's name
 * @param subjectServer the backend the subject is on
 */
public record StaffTeleportFrame(
        String originServer,
        UUID requestId,
        UUID actorUuid,
        String actorName,
        UUID subjectUuid,
        String subjectName,
        UUID destinationUuid,
        String destinationName,
        String subjectServer)
        implements NetworkMessage {

    public StaffTeleportFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(actorUuid, "actorUuid");
        Objects.requireNonNull(actorName, "actorName");
        Objects.requireNonNull(subjectUuid, "subjectUuid");
        Objects.requireNonNull(subjectName, "subjectName");
        Objects.requireNonNull(destinationUuid, "destinationUuid");
        Objects.requireNonNull(destinationName, "destinationName");
        Objects.requireNonNull(subjectServer, "subjectServer");
    }

    @Override
    public MessageType type() {
        return MessageType.STAFF_TELEPORT;
    }
}
