package com.uxplima.uxmessentials.api.bukkit.event.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/** A player asked staff for help with {@code /helpop}. */
@NullMarked
public final class UxmHelpOpEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String message;
    private final Instant raisedAt;

    public UxmHelpOpEvent(UUID requesterId, String requesterName, String message, Instant raisedAt) {
        super(requesterId, requesterName);
        this.message = Objects.requireNonNull(message, "message");
        this.raisedAt = Objects.requireNonNull(raisedAt, "raisedAt");
    }

    /** What they asked. */
    public String getMessage() {
        return message;
    }

    /** When they asked it. */
    public Instant getRaisedAt() {
        return raisedAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
