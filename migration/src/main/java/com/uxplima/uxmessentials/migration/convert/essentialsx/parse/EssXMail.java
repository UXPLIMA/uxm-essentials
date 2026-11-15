package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * One EssentialsX mail entry, source-shaped. EssentialsX writes a structured entry ({@code message},
 * {@code sender-name}, {@code sender-uuid}, {@code timestamp}, {@code expire}, {@code read}, {@code legacy}),
 * and older files hold a bare string instead. A legacy entry, either form, carries only its text and read flag:
 * EssentialsX itself ignores the sender and times on it, so they are empty here too.
 *
 * @param message the text as stored, legacy colour codes included
 * @param senderName the name the mail was sent under, empty for a legacy entry
 * @param senderId the sender's account id, empty for a legacy entry or a console sender
 * @param sentAt when the mail was sent, empty when the source did not record it
 * @param expiresAt when EssentialsX would have dropped the mail, empty when it never expires
 * @param read whether the recipient had read it
 */
@NullMarked
public record EssXMail(
        String message,
        Optional<String> senderName,
        Optional<UUID> senderId,
        Optional<Instant> sentAt,
        Optional<Instant> expiresAt,
        boolean read) {

    public EssXMail {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(sentAt, "sentAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** A legacy entry: only the text, unread, as EssentialsX treats an old string mail. */
    public static EssXMail legacy(String message, boolean read) {
        return new EssXMail(message, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), read);
    }
}
