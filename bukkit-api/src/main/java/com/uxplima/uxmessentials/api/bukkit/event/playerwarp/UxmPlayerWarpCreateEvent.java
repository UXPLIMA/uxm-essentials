package com.uxplima.uxmessentials.api.bukkit.event.playerwarp;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import org.jspecify.annotations.NullMarked;

/** A player warp was created. */
@NullMarked
public final class UxmPlayerWarpCreateEvent extends UxmPlayerWarpEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmLocation location;

    public UxmPlayerWarpCreateEvent(UUID ownerId, String ownerName, String warpName, UxmLocation location) {
        super(ownerId, ownerName, warpName);
        this.location = Objects.requireNonNull(location, "location");
    }

    /** Where the warp points. */
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
