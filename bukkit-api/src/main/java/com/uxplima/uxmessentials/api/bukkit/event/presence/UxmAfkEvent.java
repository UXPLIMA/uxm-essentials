package com.uxplima.uxmessentials.api.bukkit.event.presence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player went AFK or came back.
 *
 * <p>One event for both directions, because a listener that cares about one almost always cares about the other:
 * whatever it hid on the way out it puts back on the way in.
 */
@NullMarked
public final class UxmAfkEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean afk;
    private final Optional<String> reason;
    private final boolean automatic;
    private final Instant at;

    public UxmAfkEvent(
            UUID playerId, String playerName, boolean afk, Optional<String> reason, boolean automatic, Instant at) {
        super(playerId, playerName);
        this.afk = afk;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.automatic = automatic;
        this.at = Objects.requireNonNull(at, "at");
    }

    /** Whether they are now AFK. */
    public boolean isAfk() {
        return afk;
    }

    /** The reason they gave, if they gave one. Always empty on the way back. */
    public Optional<String> getReason() {
        return reason;
    }

    /** Whether the idle timer did this rather than the player. Always {@code false} on the way back. */
    public boolean isAutomatic() {
        return automatic;
    }

    /** When it happened. */
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
