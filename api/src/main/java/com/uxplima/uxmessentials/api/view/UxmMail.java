package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One piece of mail sitting in a player's mailbox.
 *
 * <p>Mail is text only, so the body is the whole of it. The sender's name is the one they sent under and is kept
 * even when the account behind it is gone, which is why it is always present while the id is not: mail from the
 * console has a name and no account at all.
 *
 * @param id the row id, unique within the server
 * @param recipientId the player it is for
 * @param senderId the account that sent it, or empty when the console or the server itself did
 * @param senderName the name it was sent under
 * @param body the text
 * @param sentAt when it was sent
 * @param read whether the recipient has read it
 */
public record UxmMail(
        long id,
        UUID recipientId,
        Optional<UUID> senderId,
        String senderName,
        String body,
        Instant sentAt,
        boolean read) {

    public UxmMail {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(sentAt, "sentAt");
    }

    /** Whether a player account sent this, as opposed to the console or the server. */
    public boolean fromPlayer() {
        return senderId.isPresent();
    }
}
