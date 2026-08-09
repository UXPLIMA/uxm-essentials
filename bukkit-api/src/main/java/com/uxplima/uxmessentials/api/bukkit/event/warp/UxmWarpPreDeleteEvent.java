package com.uxplima.uxmessentials.api.bukkit.event.warp;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerCancellableEvent;
import org.jspecify.annotations.NullMarked;

/** A server warp is about to be deleted. Cancel to keep it. */
@NullMarked
public final class UxmWarpPreDeleteEvent extends UxmPlayerCancellableEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String warpName;

    public UxmWarpPreDeleteEvent(UUID actorId, String actorName, String warpName) {
        super(actorId, actorName);
        this.warpName = Objects.requireNonNull(warpName, "warpName");
    }

    /** Which warp. */
    public String getWarpName() {
        return warpName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
