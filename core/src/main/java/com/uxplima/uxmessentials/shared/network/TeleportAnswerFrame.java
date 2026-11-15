package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * One step of a cross-server teleport exchange, addressed to {@link #toServer()}: the target's backend telling the
 * requester's how a request went (delivered, refused, accepted, denied, expired), the requester's withdrawing one,
 * or the backend that moved a player for a staff teleport reporting back to the staff member's. Only the addressed
 * backend acts on it.
 *
 * @param originServer the backend that answers
 * @param requestId the request or staff teleport the answer belongs to
 * @param answer the step, as the teleport context's answer name (text, so an older peer can drop a name it does not
 *     know instead of failing the frame)
 * @param toServer the backend the answer is for
 * @param aboutUuid the player the answer names in the line its reader gets
 * @param aboutName that player's name
 * @param detail extra text for the line, such as the server a player was sent to; empty when there is none
 */
public record TeleportAnswerFrame(
        String originServer,
        UUID requestId,
        String answer,
        String toServer,
        UUID aboutUuid,
        String aboutName,
        String detail)
        implements NetworkMessage {

    public TeleportAnswerFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(toServer, "toServer");
        Objects.requireNonNull(aboutUuid, "aboutUuid");
        Objects.requireNonNull(aboutName, "aboutName");
        Objects.requireNonNull(detail, "detail");
    }

    @Override
    public MessageType type() {
        return MessageType.TELEPORT_ANSWER;
    }
}
