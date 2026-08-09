package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What every moderation notification about a player has in common: who it was applied to, and when.
 *
 * <p>The subject is the punished player, not the staff member, and is named by id because a ban or a mute lands on an
 * account that is very often offline by the time you hear about it.
 */
@NullMarked
public abstract class UxmModerationEvent extends UxmPlayerEvent {

    private final Instant at;

    protected UxmModerationEvent(UUID targetId, String targetName, Instant at) {
        super(targetId, targetName);
        this.at = Objects.requireNonNull(at, "at");
    }

    /** When the action was applied. */
    public Instant getAt() {
        return at;
    }
}
