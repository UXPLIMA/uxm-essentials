package com.uxplima.uxmessentials.api.bukkit.event.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One player messaged another with {@code /msg}. The message has already been delivered.
 *
 * <p>The event's subject is the sender. Social-spy and logging listeners are the usual consumers, which is why the
 * body is carried verbatim rather than pre-formatted.
 */
@NullMarked
public final class UxmPrivateMessageEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID recipientId;
    private final String recipientName;
    private final String message;
    private final Instant sentAt;

    public UxmPrivateMessageEvent(
            UUID senderId, String senderName, UUID recipientId, String recipientName, String message, Instant sentAt) {
        super(senderId, senderName);
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");
        this.recipientName = Objects.requireNonNull(recipientName, "recipientName");
        this.message = Objects.requireNonNull(message, "message");
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
    }

    /** The id of whoever it was sent to. */
    public UUID getRecipientId() {
        return recipientId;
    }

    /** The name of whoever it was sent to. */
    public String getRecipientName() {
        return recipientName;
    }

    /** Whoever it was sent to, or {@code null} when they are offline. */
    public @Nullable Player getRecipient() {
        return Bukkit.getPlayer(recipientId);
    }

    /** What was said, as typed. */
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
