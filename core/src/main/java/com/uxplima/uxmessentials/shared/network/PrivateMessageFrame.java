package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A private message sent from the origin backend to a player online on {@code targetServer}. Every backend receives
 * the frame, and only the one named {@code targetServer} acts on it: it applies the recipient's own gates (message
 * toggle, reply routing, ignore list), delivers the line, and answers with a {@link PrivateMessageAnswerFrame}. The
 * recipient's gates live only on the backend they are connected to, which is why the decision is made there and not
 * by the sender's backend.
 *
 * @param originServer the backend the sender is on
 * @param messageId identifies this message; the answer carries it back, and a copy delivered twice is acted on once
 * @param targetServer the backend the recipient was online on when the message was sent
 * @param senderUuid the sender's account
 * @param senderName the sender's name
 * @param targetUuid the recipient's account
 * @param targetName the recipient's name
 * @param body the message as the sender's backend escaped it, ready to sit inside a catalog line
 * @param reply whether this is a {@code /reply}, which the recipient's reply-routing switch also gates
 */
public record PrivateMessageFrame(
        String originServer,
        UUID messageId,
        String targetServer,
        UUID senderUuid,
        String senderName,
        UUID targetUuid,
        String targetName,
        String body,
        boolean reply)
        implements NetworkMessage {

    public PrivateMessageFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(senderUuid, "senderUuid");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public MessageType type() {
        return MessageType.PRIVATE_MESSAGE;
    }
}
