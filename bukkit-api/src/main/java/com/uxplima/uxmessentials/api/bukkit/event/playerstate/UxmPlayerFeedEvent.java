package com.uxplima.uxmessentials.api.bukkit.event.playerstate;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A player's hunger was filled back up. */
@NullMarked
public final class UxmPlayerFeedEvent extends UxmPlayerStateEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmPlayerFeedEvent(UUID subjectId, String subjectName, UUID actorId, String actorName, Instant at) {
        super(subjectId, subjectName, actorId, actorName, at);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
