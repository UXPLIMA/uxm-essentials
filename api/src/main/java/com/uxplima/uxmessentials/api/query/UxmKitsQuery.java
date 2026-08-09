package com.uxplima.uxmessentials.api.query;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmKit;

/**
 * What kits the operator configured, and what a given player may take.
 *
 * <p>The catalogue is read from configuration and held in memory, so {@link #list()} and {@link #get(String)}
 * answer straight away and need no future. Anything that depends on a player does not: their cooldown stamps and
 * one-time marks are stored per player, so those methods hand back a future like every other stored read.
 */
public interface UxmKitsQuery {

    /** Every configured kit, whoever may claim it. Answered from memory. */
    List<UxmKit> list();

    /** The kit under this id, or empty when there is none. Answered from memory. */
    Optional<UxmKit> get(String kitId);

    /**
     * How long until this player may claim this kit again: empty when they may claim it now, or when there is no
     * such kit. A player who has never claimed it has no wait.
     */
    CompletableFuture<Optional<Duration>> cooldownRemaining(UUID playerId, String kitId);

    /**
     * Whether this player could claim this kit right now, applying the same gates the command applies: the
     * permission node, the one-time mark, the cooldown, the requirements, the stock and the price.
     */
    CompletableFuture<Boolean> canClaim(UUID playerId, String kitId);

    /** The kits this player could claim right now, which is what a menu would show them as available. */
    CompletableFuture<List<UxmKit>> claimableBy(UUID playerId);
}
