package com.uxplima.uxmessentials.api.bukkit.event.playerwarp;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerCancellableEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player warp is about to be deleted for good. Cancel to keep it.
 *
 * <p>Only the irreversible delete is asked about. Archiving is undoable by the owner themselves, so refusing it
 * would be refusing something they can simply put back.
 */
@NullMarked
public final class UxmPlayerWarpPreDeleteEvent extends UxmPlayerCancellableEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String warpName;

    public UxmPlayerWarpPreDeleteEvent(UUID ownerId, String ownerName, String warpName) {
        super(ownerId, ownerName);
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
