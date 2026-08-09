package com.uxplima.uxmessentials.api.bukkit.event.communication;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/** A player opted out of automatic broadcasts, or opted back in. */
@NullMarked
public final class UxmBroadcastOptOutEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean optedOut;
    private final Instant at;

    public UxmBroadcastOptOutEvent(UUID playerId, String playerName, boolean optedOut, Instant at) {
        super(playerId, playerName);
        this.optedOut = optedOut;
        this.at = Objects.requireNonNull(at, "at");
    }

    /** Whether they are now opted out. */
    public boolean isOptedOut() {
        return optedOut;
    }

    /** When they changed it. */
    public Instant getAt() {
        return at;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
