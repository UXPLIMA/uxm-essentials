package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import org.jspecify.annotations.NullMarked;

/** A home was created. The home exists and is already saved by the time this arrives. */
@NullMarked
public final class UxmHomeCreateEvent extends UxmHomeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmLocation location;

    public UxmHomeCreateEvent(UUID ownerId, String ownerName, int slot, UxmLocation location) {
        super(ownerId, ownerName, slot);
        this.location = Objects.requireNonNull(location, "location");
    }

    /** Where the home points. */
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
