package com.uxplima.uxmessentials.ranks.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmRanksActions;
import com.uxplima.uxmessentials.ranks.application.Prestige;
import com.uxplima.uxmessentials.ranks.application.PrestigeResult;
import com.uxplima.uxmessentials.ranks.application.RankupResult;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published rank actions, over the same use cases {@code /rankup}, {@code /setrank} and {@code /prestige} run.
 *
 * <p>The threading follows what each one touches. A rankup and a prestige evaluate requirements that can reach
 * into the player's inventory or a placeholder, so both run on the player's own thread and refuse when nobody is
 * there. A direct set writes one row and nothing else, so it runs on a worker and works for an account that is
 * offline, which is the case it exists for.
 *
 * <p>Each refusal the use cases model comes back as a failure code rather than an exception: a player who cannot
 * afford a rank is an answer, not a fault.
 *
 * <p>No calling plugin is recorded on the move. The ranks context keeps no audit of its own, and inventing one
 * here would put a record in front of consumers that the commands themselves do not write.
 */
@NullMarked
public final class RanksActions implements UxmRanksActions {

    private final RanksApiWrites writes;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public RanksActions(RanksApiWrites writes, PlayerLookup players, Scheduler scheduler) {
        this.writes = Objects.requireNonNull(writes, "writes");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> rankUp(UUID playerId) {
        return onPlayer(playerId, subject -> writes.rankup().rankUp(subject).thenApply(RanksActions::outcome));
    }

    @Override
    public CompletableFuture<UxmOutcome> setRank(UUID playerId, String rankId) {
        Objects.requireNonNull(playerId, "playerId");
        RankId target = new RankId(Objects.requireNonNull(rankId, "rankId"));
        return AsyncActions.perform(
                scheduler,
                () -> writes.setRank()
                        .setRank(playerId, target)
                        .map(rank -> UxmOutcome.ok())
                        .orElseGet(() -> UxmOutcome.failed(UxmFailure.NOT_FOUND, "no rank with id " + target.value())));
    }

    @Override
    public CompletableFuture<UxmOutcome> prestige(UUID playerId) {
        Optional<Prestige> prestige = writes.prestige();
        if (prestige.isEmpty()) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.REFUSED, "prestige is switched off in the ranks config"));
        }
        return onPlayer(playerId, subject -> prestige.get().prestige(subject).thenApply(RanksActions::outcome));
    }

    /** Every rankup refusal the use case models, mapped to the code a consumer branches on. */
    private static UxmOutcome outcome(RankupResult result) {
        return switch (result.status()) {
            case RANKED_UP -> UxmOutcome.ok();
            case ALREADY_MAX -> UxmOutcome.failed(UxmFailure.ALREADY_IN_STATE, "already on the top rank");
            case REQUIREMENTS_NOT_MET ->
                UxmOutcome.failed(UxmFailure.REFUSED, "the next rank's requirements are not met");
            case CANNOT_AFFORD -> UxmOutcome.failed(UxmFailure.INSUFFICIENT_FUNDS, "the next rank's cost was refused");
            case BUSY ->
                UxmOutcome.failed(UxmFailure.REFUSED, "the player's rank is still loading or another rankup is saving");
            case FAILED ->
                UxmOutcome.failed(UxmFailure.FAILED, "the rankup could not be stored; any charge was refunded");
        };
    }

    /** The same for a prestige. Being short of the top rung and being at the cap are both states, not refusals. */
    private static UxmOutcome outcome(PrestigeResult result) {
        return switch (result.status()) {
            case PRESTIGED -> UxmOutcome.ok();
            case NOT_AT_TOP -> UxmOutcome.failed(UxmFailure.ALREADY_IN_STATE, "not on the top rank yet");
            case MAX_LEVEL -> UxmOutcome.failed(UxmFailure.ALREADY_IN_STATE, "already at the prestige cap");
            case REQUIREMENTS_NOT_MET -> UxmOutcome.failed(UxmFailure.REFUSED, "the prestige requirements are not met");
            case CANNOT_AFFORD -> UxmOutcome.failed(UxmFailure.INSUFFICIENT_FUNDS, "the prestige cost was refused");
            case BUSY ->
                UxmOutcome.failed(
                        UxmFailure.REFUSED, "the player's rank is still loading or another prestige is saving");
            case FAILED ->
                UxmOutcome.failed(UxmFailure.FAILED, "the prestige could not be stored; any charge was refunded");
        };
    }

    /**
     * Refuse when nobody is there, otherwise start {@code write} on the thread that owns the player. The use case
     * saves on the async lane and answers once it is back there; it never completes exceptionally, so only the
     * left-before-the-hop case needs a branch of its own.
     */
    private CompletableFuture<UxmOutcome> onPlayer(UUID playerId, Write write) {
        Objects.requireNonNull(playerId, "playerId");
        if (!players.isOnline(playerId)) {
            return CompletableFuture.completedFuture(offline());
        }
        PlayerRef subject = ApiValues.subject(players, playerId);
        CompletableFuture<UxmOutcome> answer = new CompletableFuture<>();
        scheduler.onEntity(
                subject,
                () -> {
                    try {
                        var unused = write.start(subject).thenAccept(answer::complete);
                    } catch (RuntimeException failure) {
                        answer.completeExceptionally(failure);
                    }
                },
                () -> answer.complete(gone()));
        return answer;
    }

    private static UxmOutcome offline() {
        return UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player is not online to check the rank against");
    }

    private static UxmOutcome gone() {
        return UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player left before the rank could be applied");
    }

    @FunctionalInterface
    private interface Write {
        CompletableFuture<UxmOutcome> start(PlayerRef subject);
    }
}
