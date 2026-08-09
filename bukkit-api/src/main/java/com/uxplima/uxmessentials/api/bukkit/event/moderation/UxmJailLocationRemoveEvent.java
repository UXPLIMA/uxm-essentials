package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A jail was removed. Nobody can be sent to it any more. */
@NullMarked
public final class UxmJailLocationRemoveEvent extends UxmJailLocationEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmJailLocationRemoveEvent(UUID actorId, String actorName, String jail, Instant at) {
        super(actorId, actorName, jail, at);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
