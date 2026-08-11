package com.uxplima.uxmessentials.api.bukkit.event.rank;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An administrator set a player's rank directly, charging nothing and running none of the rank's actions.
 *
 * <p>The target does not have to be online, which is the usual case for a correction.
 */
@NullMarked
public final class UxmRankSetEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final @Nullable String previousRank;
    private final String rank;

    public UxmRankSetEvent(UUID playerId, String playerName, @Nullable String previousRank, String rank) {
        super(playerId, playerName);
        this.previousRank = previousRank;
        this.rank = Objects.requireNonNull(rank, "rank");
    }

    /** The id of the rank they held, empty when they had none the ladder could resolve. */
    public Optional<String> getPreviousRank() {
        return Optional.ofNullable(previousRank);
    }

    /** The id of the rank they now hold. */
    public String getRank() {
        return rank;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
