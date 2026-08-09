package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A home was renamed. */
@NullMarked
public final class UxmHomeRenameEvent extends UxmHomeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmHomeRenameEvent(UUID ownerId, String ownerName, int slot) {
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
