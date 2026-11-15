package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A staff chat line sent on the origin backend, for the staff on every other backend. Each receiver shows it to its
 * own staff with the origin server in the line.
 *
 * @param originServer the backend the sender is on
 * @param messageId identifies the line, so a copy delivered twice is shown once
 * @param senderUuid the sender's account
 * @param senderName the sender's name
 * @param message the line as typed
 */
public record StaffChatFrame(String originServer, UUID messageId, UUID senderUuid, String senderName, String message)
        implements NetworkMessage {

    public StaffChatFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(senderUuid, "senderUuid");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(message, "message");
    }

    @Override
    public MessageType type() {
        return MessageType.STAFF_CHAT;
    }
}
