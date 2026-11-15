package com.uxplima.uxmessentials.migration.convert.map;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * One piece of mail a source held for a player, already stripped of the source's colour codes but still the
 * plain text somebody typed. It is not a {@code MailItem} yet: the writer escapes the text and the sender name
 * for the MiniMessage template the mail is drawn in, fits the body to its column, and stamps a send time when
 * the source had none.
 *
 * @param senderId the sender's account id, empty for a console sender or a source that did not record it
 * @param senderName the name the mail was sent under
 * @param text the mail text as typed, without colour codes
 * @param sentAt when the mail was sent, empty when the source did not record it
 * @param expiresAt when the source would have dropped the mail, empty when it never expires
 * @param read whether the recipient had read it
 */
@NullMarked
public record ImportedMail(
        Optional<UUID> senderId,
        String senderName,
        String text,
        Optional<Instant> sentAt,
        Optional<Instant> expiresAt,
        boolean read) {

    public ImportedMail {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(sentAt, "sentAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (senderName.isBlank()) {
            throw new IllegalArgumentException("mail sender name must not be blank");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("mail text must not be blank");
        }
    }
}
