package com.uxplima.uxmessentials.api.bukkit.event.vote;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A vote arrived and was credited to a player.
 *
 * <p>Fires for offline votes too, at the moment they are counted rather than at the moment the player next logs in.
 */
@NullMarked
public final class UxmVoteReceiveEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String service;

    public UxmVoteReceiveEvent(UUID voterId, String voterName, String service) {
        super(voterId, voterName);
        this.service = Objects.requireNonNull(service, "service");
    }

    /** The voting site it came from. */
    public String getService() {
        return service;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
