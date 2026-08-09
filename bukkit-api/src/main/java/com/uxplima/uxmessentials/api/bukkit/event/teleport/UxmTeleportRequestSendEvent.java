package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmTeleportRequestDirection;
import org.jspecify.annotations.NullMarked;

/** A teleport request was made and is now waiting for the target to answer it. */
@NullMarked
public final class UxmTeleportRequestSendEvent extends UxmTeleportRequestEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmTeleportRequestDirection direction;
    private final Instant expiresAt;

    public UxmTeleportRequestSendEvent(
            UUID requestId,
            UUID requesterId,
            String requesterName,
            UUID targetId,
            String targetName,
            UxmTeleportRequestDirection direction,
            Instant expiresAt) {
        super(requestId, requesterId, requesterName, targetId, targetName);
        this.direction = Objects.requireNonNull(direction, "direction");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Which way the teleport would go if accepted. */
    public UxmTeleportRequestDirection getDirection() {
        return direction;
    }

    /** When the request lapses if nobody answers it. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
