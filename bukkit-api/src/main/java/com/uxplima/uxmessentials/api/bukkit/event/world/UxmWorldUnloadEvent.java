package com.uxplima.uxmessentials.api.bukkit.event.world;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A managed world was unloaded. Its data is still on disk. */
@NullMarked
public final class UxmWorldUnloadEvent extends UxmWorldEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmWorldUnloadEvent(String worldName) {
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
