package com.uxplima.uxmessentials.api.bukkit.event.skin;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player is wearing a different skin.
 *
 * <p>Fires for every door into a change: the command, a staff member dressing somebody else, and the login path
 * that dresses a player who has chosen nothing. By the time it fires the new skin is already on the player, so a
 * listener mirroring the face elsewhere (a tab entry, a hologram, a cached head) can read it straight off this
 * event.
 *
 * <p>A cleared skin fires this too, with {@link #isCleared()} true: the player has dropped their own choice and is
 * wearing whatever the join order gave them instead.
 */
@NullMarked
public final class UxmSkinChangeEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String sourceType;
    private final String sourceValue;
    private final boolean cleared;

    public UxmSkinChangeEvent(
            UUID playerId, String playerName, String sourceType, String sourceValue, boolean cleared) {
        super(playerId, playerName);
        this.sourceType = sourceType;
        this.sourceValue = sourceValue;
        this.cleared = cleared;
    }

    /**
     * Where the new skin came from: {@code BY_NAME}, {@code BY_URL}, {@code BY_FILE}, {@code BEDROCK} or
     * {@code FALLBACK}. Empty on a clear, which has no source of its own.
     */
    public String getSourceType() {
        return sourceType;
    }

    /** The one value that source carries: a username, a url, a file name, an xuid or a pool entry. */
    public String getSourceValue() {
        return sourceValue;
    }

    /** True when the player dropped their own choice rather than picking a new skin. */
    public boolean isCleared() {
        return cleared;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
