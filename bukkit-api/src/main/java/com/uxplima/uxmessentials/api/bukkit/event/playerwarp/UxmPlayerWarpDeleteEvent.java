package com.uxplima.uxmessentials.api.bukkit.event.playerwarp;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A player warp was deleted. */
@NullMarked
public final class UxmPlayerWarpDeleteEvent extends UxmPlayerWarpEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmPlayerWarpDeleteEvent(UUID ownerId, String ownerName, String warpName) {
        super(ownerId, ownerName, warpName);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
