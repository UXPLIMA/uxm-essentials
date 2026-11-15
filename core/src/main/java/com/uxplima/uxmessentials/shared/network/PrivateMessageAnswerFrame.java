package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * What the recipient's backend did with a {@link PrivateMessageFrame}. The sender's backend uses it to tell the
 * sender how the message went; every other backend uses it to show the message to its social spies, which is why the
 * frame repeats the parties and the body instead of naming the message alone.
 *
 * @param originServer the backend that answered: the one the recipient is on
 * @param messageId the message this answers
 * @param outcome what happened, as the outcome's name (text, so this shared frame never depends on a context's enum
 *     and a newer peer's outcome reads as unknown rather than as a broken frame)
 * @param senderUuid the sender's account
 * @param senderName the sender's name
 * @param targetUuid the recipient's account
 * @param targetName the recipient's name
 * @param body the message body
 * @param afk whether the recipient was away when the message arrived
 * @param afkReason the reason the recipient gave for being away; blank when none, or when not away
 */
public record PrivateMessageAnswerFrame(
        String originServer,
        UUID messageId,
        String outcome,
        UUID senderUuid,
        String senderName,
        UUID targetUuid,
        String targetName,
        String body,
        boolean afk,
        String afkReason)
        implements NetworkMessage {

    public PrivateMessageAnswerFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(senderUuid, "senderUuid");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(afkReason, "afkReason");
    }

    @Override
    public MessageType type() {
        return MessageType.PRIVATE_MESSAGE_ANSWER;
    }
}
