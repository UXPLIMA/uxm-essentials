package com.uxplima.uxmessentials.api.bukkit.event.vote;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import org.jspecify.annotations.NullMarked;

/** The vote party threshold was reached and the party fired. Server-wide, so no player is its subject. */
@NullMarked
public final class UxmVotePartyEvent extends UxmEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int threshold;

    public UxmVotePartyEvent(int threshold) {
        this.threshold = threshold;
    }

    /** The vote count that set it off. */
    public int getThreshold() {
        return threshold;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
