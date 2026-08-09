package com.uxplima.uxmessentials.api.bukkit.event.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A piece of mail was put in a player's inbox.
 *
 * <p>The recipient is the subject and is very often offline, which is the whole point of mail. The sender's id is
 * optional because the console can send mail too.
 */
@NullMarked
public final class UxmMailDeliverEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Optional<UUID> senderId;
    private final String senderName;
    private final String message;
    private final Instant sentAt;

    public UxmMailDeliverEvent(
            UUID recipientId,
            String recipientName,
            Optional<UUID> senderId,
            String senderName,
            String message,
            Instant sentAt) {
        super(recipientId, recipientName);
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.senderName = Objects.requireNonNull(senderName, "senderName");
        this.message = Objects.requireNonNull(message, "message");
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
    }

    /** The sender's id, or empty when the console sent it. */
    public Optional<UUID> getSenderId() {
        return senderId;
    }

    /** The sender's name. */
    public String getSenderName() {
        return senderName;
    }

    /** What the mail says. */
    public String getMessage() {
        return message;
    }

    /** When it was sent. */
    public Instant getSentAt() {
        return sentAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
