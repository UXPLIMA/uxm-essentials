package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Moving a player along the rank ladder.
 *
 * <p>Two different verbs, deliberately kept apart. {@link #rankUp} runs the real pipeline: requirements, cost,
 * pointer, the rank's actions, exactly as the player typing {@code /rankup} would, and it refuses with
 * {@code refused} when they do not qualify or cannot pay. {@link #setRank} is the administrator's escape hatch:
 * it writes the pointer and nothing else, charging nothing and running no rank actions.
 *
 * <p>{@link #setRank} works on an offline account, because a stored rank is a fact about an account rather than
 * a session. {@link #rankUp} and {@link #prestige} need the player online and answer {@code player-offline}
 * otherwise: a rank requirement can name their inventory or a placeholder, and those cannot be read for somebody
 * who is not there.
 *
 * <pre>{@code
 * actions.ranks().ifPresent(ranks -> ranks.setRank(playerId, "vip"));
 * }</pre>
 */
public interface UxmRanksActions {

    /**
     * Advance this player one rung, charging the cost and running the rank's actions.
     *
     * <p>{@code not-found} when the ladder is empty, {@code already-in-state} at the top of the ladder,
     * {@code insufficient-funds} when they cannot pay, {@code refused} when a requirement is not met, when their rank
     * has not finished loading after they joined, or when another rankup for them is still being saved (the last two
     * clear within moments, so a retry works). {@code failed} when storage failed while charging or saving: the rank
     * did not move and any cost taken was refunded. The future completes once the new rank is saved.
     */
    CompletableFuture<UxmOutcome> rankUp(UUID playerId);

    /**
     * Set this player's rank directly, keeping their prestige level, charging nothing.
     *
     * <p>{@code not-found} when {@code rankId} is not a rung on the ladder, in which case nothing is written.
     */
    CompletableFuture<UxmOutcome> setRank(UUID playerId, String rankId);

    /**
     * Prestige this player: reset them to the first rung one prestige level higher, charging the prestige cost.
     *
     * <p>{@code refused} when prestige is switched off, when a requirement is not met or when their rank has not
     * finished loading after they joined (a retry a moment later works), {@code already-in-state} when they are not
     * at the top or are already at the prestige cap, {@code insufficient-funds} when they cannot pay.
     */
    CompletableFuture<UxmOutcome> prestige(UUID playerId);
}
