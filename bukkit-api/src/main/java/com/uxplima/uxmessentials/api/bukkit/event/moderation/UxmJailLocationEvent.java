package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A jail was defined or removed as a place.
 *
 * <p>Unlike the rest of this package the player named is the staff member who made the change, because a jail
 * location is a piece of server configuration rather than something applied to somebody.
 */
@NullMarked
public abstract class UxmJailLocationEvent extends UxmPlayerEvent {

    private final String jail;
    private final Instant at;

    protected UxmJailLocationEvent(UUID actorId, String actorName, String jail, Instant at) {
        super(actorId, actorName);
        this.jail = Objects.requireNonNull(jail, "jail");
        this.at = Objects.requireNonNull(at, "at");
    }

    /** The jail's name. */
    public String getJail() {
        return jail;
    }

    /** When the change was made. */
    public Instant getAt() {
        return at;
    }
}
