package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** The target accepted the request. The teleport itself follows as its own event. */
@NullMarked
public final class UxmTeleportRequestAcceptEvent extends UxmTeleportRequestEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmTeleportRequestAcceptEvent(
            UUID requestId, UUID requesterId, String requesterName, UUID targetId, String targetName) {
        super(requestId, requesterId, requesterName, targetId, targetName);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
