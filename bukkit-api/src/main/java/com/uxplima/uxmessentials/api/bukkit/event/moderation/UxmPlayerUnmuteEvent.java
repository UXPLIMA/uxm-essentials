package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A player's mute was lifted. */
@NullMarked
public final class UxmPlayerUnmuteEvent extends UxmModerationEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmPlayerUnmuteEvent(UUID targetId, String targetName, Instant at) {
        super(targetId, targetName, at);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
