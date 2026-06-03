package com.uxplima.uxmessentials.scoreboard.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The per-player "scoreboard display hidden" preference. A fresh player defaults to <em>shown</em>; running
 * {@code /scoreboard} flips the bit. The choice is a durable preference that survives relog, so the adapter backs it
 * with PDC rather than transient session state.
 *
 * <p>The render loop reads {@link #hidden(PlayerRef)} per viewer each refresh to decide whether to render or tear
 * down a player's board; {@link #toggle(PlayerRef)} returns the new hidden state so the command can re-render or
 * clear the board immediately rather than waiting for the next tick.
 */
public interface ScoreboardVisibilityStore {

    /** True when {@code who} has hidden their display. A player who never toggled is shown (returns {@code false}). */
    boolean hidden(PlayerRef who);

    /** Flip {@code who}'s visibility and return the new state: {@code true} when now hidden, {@code false} when shown. */
    boolean toggle(PlayerRef who);

    /** Drop any in-memory bookkeeping for {@code who} on quit. The durable preference itself is kept. */
    void forget(PlayerRef who);
}
