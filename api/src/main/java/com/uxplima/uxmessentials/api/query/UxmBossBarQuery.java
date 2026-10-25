package com.uxplima.uxmessentials.api.query;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The boss bars a server has written, and the ones on a player's screen.
 *
 * <p>A player's answers are read on the thread that owns them, because the choice to hide the bars is stored on the
 * player and the bars on their screen are drawn there.
 */
public interface UxmBossBarQuery {

    /** The ids of the bars written in the module's file, top first. */
    List<String> bars();

    /** Whether this player has hidden their bars, or empty when they are offline. */
    CompletableFuture<Optional<Boolean>> hidden(UUID playerId);

    /** The ids of the bars on this player's screen, top first; empty when they are offline or see none. */
    CompletableFuture<List<String>> shown(UUID playerId);

    /**
     * The time left on bar {@code barId} for this player: an announcement made to them or to everybody, or the bar's
     * own end. Empty when the bar has no clock running for them.
     */
    CompletableFuture<Optional<Duration>> remaining(UUID playerId, String barId);
}
