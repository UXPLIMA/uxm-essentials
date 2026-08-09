package com.uxplima.uxmessentials.api.bukkit.event.world;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A world was dropped from uxmEssentials' registry. The folder is untouched. */
@NullMarked
public final class UxmWorldUnregisterEvent extends UxmWorldEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmWorldUnregisterEvent(String worldName) {
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
