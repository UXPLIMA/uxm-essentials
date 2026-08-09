package com.uxplima.uxmessentials.api.bukkit.event.world;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmWorldAccess;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A player was refused entry to a managed world.
 *
 * <p>The refusal has already happened: the player is still where they were. This is the one world event with a player
 * at its centre, so it is delivered on that player's region rather than globally.
 */
@NullMarked
public final class UxmWorldEntryDeniedEvent extends UxmWorldEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final String playerName;
    private final UxmWorldAccess reason;

    public UxmWorldEntryDeniedEvent(String worldName, UUID playerId, String playerName, UxmWorldAccess reason) {
        super(worldName);
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** The id of the player who was refused. */
    public UUID getPlayerId() {
        return playerId;
    }

    /** The name of the player who was refused. */
    public String getPlayerName() {
        return playerName;
    }

    /** The player who was refused, or {@code null} if they have since logged out. */
    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    /** Why they were refused. Never {@link UxmWorldAccess#ALLOWED}. */
    public UxmWorldAccess getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
