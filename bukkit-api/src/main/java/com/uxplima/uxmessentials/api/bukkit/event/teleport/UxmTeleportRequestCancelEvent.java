package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** The requester withdrew the request before it was answered. */
@NullMarked
public final class UxmTeleportRequestCancelEvent extends UxmTeleportRequestEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmTeleportRequestCancelEvent(
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
