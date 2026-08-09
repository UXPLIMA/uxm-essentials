package com.uxplima.uxmessentials.api.bukkit.event.vault;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A vault's contents were saved after being changed. What is in the database is now the new contents. */
@NullMarked
public final class UxmVaultContentsChangeEvent extends UxmVaultEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmVaultContentsChangeEvent(UUID ownerId, String ownerName, int index, Instant at) {
        super(ownerId, ownerName, index, at);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
