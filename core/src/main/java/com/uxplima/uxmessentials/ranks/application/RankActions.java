package com.uxplima.uxmessentials.ranks.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.ranks.application.port.PendingRankActions;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Gets a rank's actions to the player they are for, wherever that player is. A player on this server has them run at
 * once on their own thread. A player who is not here (they left while the rank was saved, moved to another backend,
 * or are offline) has them kept in the shared {@link PendingRankActions} store, and they run on that player's next
 * join on any server of the database. The whole chain is kept, not only the lines that name the player, so a
 * permission or chance gate still decides what follows it and the lines keep their order.
 *
 * <p>A line is never run twice: a join takes the lines out of the store before running them, and a player who is
 * gone again by the time they would run has them kept once more.
 */
public final class RankActions {

    private final RankActionRunner runner;
    private final PendingRankActions pending;
    private final Scheduler scheduler;
    private final Logger log;

    public RankActions(RankActionRunner runner, PendingRankActions pending, Scheduler scheduler, Logger log) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Run {@code lines} for {@code who} now when they are {@code present} on this server, on their thread, or keep
     * them for their next join. Never throws: a broken line must not undo a rank that is already saved.
     */
    public void deliver(PlayerRef who, List<String> lines, boolean present) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            return;
        }
        if (present && runHere(who, lines)) {
            return;
        }
        keep(who, lines);
    }

    /**
     * {@code who} has joined this server: run what was kept for them. The store is read on the async lane and the
     * lines run on the player's own thread.
     */
    public void joined(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.async(() -> {
            List<String> lines;
            try {
                lines = pending.take(who.uuid());
            } catch (RuntimeException failure) {
                log.error("failed to read the kept rank actions of player " + who.uuid(), failure);
                return;
            }
            if (lines.isEmpty()) {
                return;
            }
            scheduler.onEntity(who, () -> deliver(who, lines, true), () -> keep(who, lines));
        });
    }

    /** Run on this server; false when the player is not here after all. */
    private boolean runHere(PlayerRef who, List<String> lines) {
        try {
            return runner.run(who, lines);
        } catch (RuntimeException failure) {
            // The rank is already paid for and saved; a broken action line must not hide that from the player.
            log.error("rank actions failed for player " + who.uuid(), failure);
            return true;
        }
    }

    /** Keep the lines for the next join, written on the async lane. */
    private void keep(PlayerRef who, List<String> lines) {
        List<String> kept = List.copyOf(lines);
        scheduler.async(() -> {
            try {
                pending.keep(who.uuid(), kept);
            } catch (RuntimeException failure) {
                log.error("failed to keep the rank actions of player " + who.uuid() + " for their next join", failure);
            }
        });
    }
}
