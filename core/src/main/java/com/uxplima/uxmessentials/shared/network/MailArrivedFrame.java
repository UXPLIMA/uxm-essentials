package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * Mail was left on the origin backend for a player who may be online on another one. The backend the recipient is on
 * tells them it arrived; the mail itself is already in the shared database.
 *
 * @param originServer the backend the mail was left on
 * @param noticeId identifies the notice, so a copy delivered twice is shown once
 * @param recipientUuid the recipient's account
 * @param recipientName the recipient's name
 * @param senderName the name the mail was sent under
 */
public record MailArrivedFrame(
        String originServer, UUID noticeId, UUID recipientUuid, String recipientName, String senderName)
        implements NetworkMessage {

    public MailArrivedFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(noticeId, "noticeId");
        Objects.requireNonNull(recipientUuid, "recipientUuid");
        Objects.requireNonNull(recipientName, "recipientName");
        Objects.requireNonNull(senderName, "senderName");
    }

    @Override
    public MessageType type() {
        return MessageType.MAIL_ARRIVED;
    }
}
