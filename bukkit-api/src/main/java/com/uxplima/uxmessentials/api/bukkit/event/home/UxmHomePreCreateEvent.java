package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import org.jspecify.annotations.NullMarked;

/**
 * A home is about to be created. Cancel to refuse it.
 *
 * <p>Fired once uxmEssentials' own rules have all passed: the slot is in range and free, the owner is inside their
 * limit, the position is one homes allows, and nothing has been charged yet.
 */
@NullMarked
public final class UxmHomePreCreateEvent extends UxmHomePreEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmLocation location;

    public UxmHomePreCreateEvent(UUID ownerId, String ownerName, int slot, UxmLocation location) {
        super(ownerId, ownerName, slot);
        this.location = Objects.requireNonNull(location, "location");
    }

    /** Where the home would point. */
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
