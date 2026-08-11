package com.uxplima.uxmessentials.invrollback.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmInvRollbackActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotRestorer;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * The published restore, over the same flow the {@code /invrestore} preview's restore button runs.
 *
 * <p>Which means the same safety copy: what the player is holding is frozen as its own snapshot before it is
 * overwritten. A second implementation would be the one that forgot to.
 *
 * <p>The flow schedules its own hops, three of them, so this does not wrap it in another. The future completes
 * when the last hop reports back, which is after the items are set and the fact is published.
 */
@NullMarked
public final class InvRollbackActions implements UxmInvRollbackActions {

    private final SnapshotRestorer restorer;
    private final PlayerLookup players;

    public InvRollbackActions(SnapshotRestorer restorer, PlayerLookup players) {
        this.restorer = Objects.requireNonNull(restorer, "restorer");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public CompletableFuture<UxmOutcome> restore(UUID playerId, UUID snapshotId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshotId, "snapshotId");
        CompletableFuture<UxmOutcome> answer = new CompletableFuture<>();
        restorer.restore(
                ApiValues.subject(players, playerId),
                SnapshotId.of(snapshotId),
                outcome -> answer.complete(translate(outcome)));
        return answer;
    }

    private static UxmOutcome translate(SnapshotRestorer.Outcome outcome) {
        if (outcome instanceof SnapshotRestorer.Outcome.Restored) {
            return UxmOutcome.ok();
        }
        if (outcome instanceof SnapshotRestorer.Outcome.Gone) {
            return UxmOutcome.failed(UxmFailure.NOT_FOUND, "no snapshot with that id is held any more");
        }
        return UxmOutcome.failed(
                UxmFailure.PLAYER_OFFLINE, "a snapshot is applied to a live inventory, and they are not online");
    }
}
