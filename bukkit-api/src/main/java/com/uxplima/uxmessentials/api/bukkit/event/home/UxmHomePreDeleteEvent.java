package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/**
 * A home is about to be deleted. Cancel to keep it.
 *
 * <p>Fired once the home is known to exist, and before the row and its invites are removed.
 */
@NullMarked
public final class UxmHomePreDeleteEvent extends UxmHomePreEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmHomePreDeleteEvent(UUID ownerId, String ownerName, int slot) {
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
