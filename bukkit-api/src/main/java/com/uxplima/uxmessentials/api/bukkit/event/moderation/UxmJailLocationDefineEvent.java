package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A jail was given a location, or had its location moved. */
@NullMarked
public final class UxmJailLocationDefineEvent extends UxmJailLocationEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmJailLocationDefineEvent(UUID actorId, String actorName, String jail, Instant at) {
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
