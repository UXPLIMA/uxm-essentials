package com.uxplima.uxmessentials.invrollback.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmInvRollbackQuery;
import com.uxplima.uxmessentials.api.view.UxmSnapshot;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The published snapshot list, over the same repository {@code /invrestore} lists from.
 *
 * <p>A database read, so it goes to a worker. The rows come back newest first and already bounded by the retention
 * rules, so nothing here trims them further: what the server kept is what the caller is told about.
 *
 * <p>The serialized inventory is read and dropped rather than carried. It is the bulk of every row and nothing on
 * this side of the boundary could do anything with it.
 */
@NullMarked
public final class InvRollbackQueries implements UxmInvRollbackQuery {

    private final SnapshotRepository repository;
    private final Scheduler scheduler;

    public InvRollbackQueries(SnapshotRepository repository, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<List<UxmSnapshot>> of(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(
                scheduler,
                () -> repository.list(playerId).stream()
                        .map(InvRollbackQueries::view)
                        .toList());
    }

    private static UxmSnapshot view(Snapshot snapshot) {
        return new UxmSnapshot(
                snapshot.id().value(),
                snapshot.owner(),
                InvRollbackEventBridges.cause(snapshot.cause()),
                snapshot.createdAt());
    }
}
