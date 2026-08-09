package com.uxplima.uxmessentials.api.bukkit.event.world;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A managed world was loaded and is now available. */
@NullMarked
public final class UxmWorldLoadEvent extends UxmWorldEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmWorldLoadEvent(String worldName) {
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
