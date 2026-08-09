package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmBackCause;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import org.jspecify.annotations.NullMarked;

/** A position was recorded as the one the player's next {@code /back} would return to. */
@NullMarked
public final class UxmBackLocationCaptureEvent extends UxmTeleportEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmLocation location;
    private final UxmBackCause cause;

    public UxmBackLocationCaptureEvent(UUID playerId, String playerName, UxmLocation location, UxmBackCause cause) {
        super(playerId, playerName);
        this.location = Objects.requireNonNull(location, "location");
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    /** The position that was captured. */
    public UxmLocation getLocation() {
        return location;
    }

    /** Why it was captured. */
    public UxmBackCause getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
