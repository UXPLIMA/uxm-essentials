package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import org.jspecify.annotations.NullMarked;

/**
 * A home is about to be re-anchored to a new position. Cancel to leave it where it is.
 *
 * <p>Fired once the home is known to exist and the new position is one homes allows, and before the owner is
 * charged for the move.
 */
@NullMarked
public final class UxmHomePreRelocateEvent extends UxmHomePreEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmLocation location;

    public UxmHomePreRelocateEvent(UUID ownerId, String ownerName, int slot, UxmLocation location) {
        super(ownerId, ownerName, slot);
        this.location = Objects.requireNonNull(location, "location");
    }

    /** Where the home would move to. Its current position is not carried: read it before cancelling if you need it. */
    public UxmLocation getLocation() {
        return location;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
