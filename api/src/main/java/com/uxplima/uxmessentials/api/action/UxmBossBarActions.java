package com.uxplima.uxmessentials.api.action;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hiding and showing a player's boss bars, drawing them now, and putting a bar up for a while.
 *
 * <p>The bars are drawn again on a timer, so a change to what one says shows within one refresh interval without
 * asking for anything here. {@link #refresh} is for when that wait is too long. Hiding and showing write the same
 * choice {@code /bossbars} does, kept with the player across a relog.
 */
public interface UxmBossBarActions {

    /** Draw this player's bars now rather than at the next refresh. */
    CompletableFuture<UxmOutcome> refresh(UUID playerId);

    /** Hide this player's bars. Refused with {@link UxmFailure#ALREADY_IN_STATE} when they are already hidden. */
    CompletableFuture<UxmOutcome> hide(UUID playerId);

    /** Show this player's bars. Refused with {@link UxmFailure#ALREADY_IN_STATE} when they are already shown. */
    CompletableFuture<UxmOutcome> show(UUID playerId);

    /**
     * Put bar {@code barId} up for everybody for {@code length}, as {@code /bossbars announce} does. Refused with
     * {@link UxmFailure#NOT_FOUND} for a bar the file does not write, and {@link UxmFailure#REFUSED} for a length
     * that is not positive.
     */
    CompletableFuture<UxmOutcome> announce(String barId, Duration length);

    /**
     * Put bar {@code barId} up for one player for {@code length}, as {@code /bossbars send} does. Refused like
     * {@link #announce}, and with {@link UxmFailure#PLAYER_OFFLINE} when the player is not here.
     */
    CompletableFuture<UxmOutcome> send(UUID playerId, String barId, Duration length);
}
