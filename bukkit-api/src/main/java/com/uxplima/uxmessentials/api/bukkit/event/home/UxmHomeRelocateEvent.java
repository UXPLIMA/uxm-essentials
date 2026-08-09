package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** An existing home was moved to a new place, keeping its slot and its name. */
@NullMarked
public final class UxmHomeRelocateEvent extends UxmHomeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmHomeRelocateEvent(UUID ownerId, String ownerName, int slot) {
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
