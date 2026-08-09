package com.uxplima.uxmessentials.api.bukkit.event.playerstate;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A player was healed back to full. */
@NullMarked
public final class UxmPlayerHealEvent extends UxmPlayerStateEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmPlayerHealEvent(UUID subjectId, String subjectName, UUID actorId, String actorName, Instant at) {
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
