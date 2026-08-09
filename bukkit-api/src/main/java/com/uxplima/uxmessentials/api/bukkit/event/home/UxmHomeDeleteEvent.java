package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A home was deleted. It is already gone by the time this arrives, so its location is no longer available. */
@NullMarked
public final class UxmHomeDeleteEvent extends UxmHomeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmHomeDeleteEvent(UUID ownerId, String ownerName, int slot) {
        super(ownerId, ownerName, slot);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
