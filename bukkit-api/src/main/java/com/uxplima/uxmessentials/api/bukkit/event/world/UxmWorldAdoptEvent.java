package com.uxplima.uxmessentials.api.bukkit.event.world;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A world the server already had was taken under uxmEssentials' management. */
@NullMarked
public final class UxmWorldAdoptEvent extends UxmWorldEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmWorldAdoptEvent(String worldName) {
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
