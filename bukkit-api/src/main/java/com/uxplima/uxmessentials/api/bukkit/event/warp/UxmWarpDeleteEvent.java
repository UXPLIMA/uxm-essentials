package com.uxplima.uxmessentials.api.bukkit.event.warp;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A server warp was deleted. The player named is whoever removed it. */
@NullMarked
public final class UxmWarpDeleteEvent extends UxmWarpEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmWarpDeleteEvent(UUID actorId, String actorName, String warpName) {
        super(actorId, actorName, warpName);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
