package com.uxplima.uxmessentials.api.bukkit.event.hologram;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import org.jspecify.annotations.NullMarked;

/** A hologram was created. */
@NullMarked
public final class UxmHologramCreateEvent extends UxmHologramEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmLocation location;

    public UxmHologramCreateEvent(UUID actorId, String actorName, String hologramName, UxmLocation location) {
        super(actorId, actorName, hologramName);
        this.location = Objects.requireNonNull(location, "location");
    }

    /** Where the hologram stands. */
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
