package com.uxplima.uxmessentials.api.bukkit.event.playerstate;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A player's invulnerability was switched on or off. */
@NullMarked
public final class UxmPlayerGodToggleEvent extends UxmPlayerStateEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean enabled;

    public UxmPlayerGodToggleEvent(
            UUID subjectId, String subjectName, UUID actorId, String actorName, boolean enabled, Instant at) {
        super(subjectId, subjectName, actorId, actorName, at);
        this.enabled = enabled;
    }

    /** Whether it is now on. */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
