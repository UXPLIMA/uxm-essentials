package com.uxplima.uxmessentials.api.bukkit.event.rank;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player climbed one rung of the ladder, having met the requirements and paid the cost.
 *
 * <p>Fires for the autorank scan's promotions too, since those run the same pipeline. An administrator setting a
 * rank by hand is {@link UxmRankSetEvent} instead, so a reward handed out here is not handed out again by a
 * correction.
 *
 * <p>Fires after the new rank is stored, so reading the player's rank in a handler reads the new one.
 */
@NullMarked
public final class UxmRankUpEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String fromRank;
    private final String toRank;

    public UxmRankUpEvent(UUID playerId, String playerName, String fromRank, String toRank) {
        super(playerId, playerName);
        this.fromRank = Objects.requireNonNull(fromRank, "fromRank");
        this.toRank = Objects.requireNonNull(toRank, "toRank");
    }

    /** The id of the rank they held. */
    public String getFromRank() {
        return fromRank;
    }

    /** The id of the rank they now hold. */
    public String getToRank() {
        return toRank;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
