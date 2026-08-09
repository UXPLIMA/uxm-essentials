package com.uxplima.uxmessentials.api.query;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmPlayerState;

/**
 * The switches uxmEssentials is holding for a player: god mode, flight, speeds, game mode.
 *
 * <p>Answers straight away, and only for a player who is online. None of this is written down: the switches are
 * seeded when a player joins, changed by the commands, and dropped when they leave, so a player who is not here
 * has no state to report rather than a stale one.
 */
public interface UxmPlayerStateQuery {

    /** What the plugin is holding for this player, or empty when they are not online. */
    Optional<UxmPlayerState> of(UUID playerId);
}
