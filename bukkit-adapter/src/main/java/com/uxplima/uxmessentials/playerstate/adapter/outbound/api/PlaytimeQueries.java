package com.uxplima.uxmessentials.playerstate.adapter.outbound.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmPlaytimeQuery;
import com.uxplima.uxmessentials.api.view.UxmPlaytime;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The published playtime query, over the same per-day ledger {@code /playtime} sums.
 *
 * <p>The windows are anchored on the server's calendar day rather than on a rolling clock, and the clock is the
 * injected one, so a consumer asking for "today" gets the day the plugin itself would name.
 */
@NullMarked
public final class PlaytimeQueries implements UxmPlaytimeQuery {

    private final PlaytimeRepository repository;
    private final Scheduler scheduler;
    private final Clock clock;

    public PlaytimeQueries(PlaytimeRepository repository, Scheduler scheduler, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<UxmPlaytime> of(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> view(repository.summaryOf(playerId, LocalDate.now(clock))));
    }

    private static UxmPlaytime view(PlaytimeSummary summary) {
        return new UxmPlaytime(
                summary.todayActive(),
                summary.todayAfk(),
                summary.weekActive(),
                summary.weekAfk(),
                summary.monthActive(),
                summary.monthAfk(),
                summary.totalActive(),
                summary.totalAfk());
    }
}
