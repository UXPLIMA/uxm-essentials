package com.uxplima.uxmessentials.api.bukkit.event.world;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A world was created by uxmEssentials and registered with it. */
@NullMarked
public final class UxmWorldCreateEvent extends UxmWorldEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmWorldCreateEvent(String worldName) {
        super(worldName);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
