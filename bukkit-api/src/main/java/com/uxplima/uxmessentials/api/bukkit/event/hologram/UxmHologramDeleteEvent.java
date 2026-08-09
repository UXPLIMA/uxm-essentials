package com.uxplima.uxmessentials.api.bukkit.event.hologram;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A hologram was deleted. */
@NullMarked
public final class UxmHologramDeleteEvent extends UxmHologramEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmHologramDeleteEvent(UUID actorId, String actorName, String hologramName) {
        super(actorId, actorName, hologramName);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
