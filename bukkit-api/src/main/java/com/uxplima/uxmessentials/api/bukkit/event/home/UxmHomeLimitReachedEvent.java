package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player tried to set one home too many. Nothing was created; this is the fact that they hit their cap, which is
 * what a plugin selling extra home slots listens for.
 *
 * <p>It carries no slot, because the home that would have used one was never made.
 */
@NullMarked
public final class UxmHomeLimitReachedEvent extends UxmEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int currentCount;
    private final int limit;

    public UxmHomeLimitReachedEvent(UUID ownerId, String ownerName, int currentCount, int limit) {
        super(ownerId, ownerName);
        this.currentCount = currentCount;
        this.limit = limit;
    }

    /** How many homes the player already has. */
    public int getCurrentCount() {
        return currentCount;
    }

    /** How many they are allowed, as their permission nodes resolved it. */
    public int getLimit() {
        return limit;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
