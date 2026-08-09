package com.uxplima.uxmessentials.api.bukkit.event.warp;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerCancellableEvent;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import org.jspecify.annotations.NullMarked;

/**
 * A server warp is about to be created. Cancel to refuse it.
 *
 * <p>Only a genuinely new warp is asked about: re-anchoring an existing one to a new position is a move, and the
 * warp already exists by then.
 */
@NullMarked
public final class UxmWarpPreCreateEvent extends UxmPlayerCancellableEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String warpName;
    private final UxmLocation location;

    public UxmWarpPreCreateEvent(UUID ownerId, String ownerName, String warpName, UxmLocation location) {
        super(ownerId, ownerName);
        this.warpName = Objects.requireNonNull(warpName, "warpName");
        this.location = Objects.requireNonNull(location, "location");
    }

    /** What the warp would be called. */
    public String getWarpName() {
        return warpName;
    }

    /** Where it would point. */
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
