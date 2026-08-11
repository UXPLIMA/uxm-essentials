package com.uxplima.uxmessentials.ranks.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
        return onPlayer(playerId, subject -> outcome(writes.rankup().rankUp(subject)));
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
        return onPlayer(playerId, subject -> outcome(prestige.get().prestige(subject)));
    }

    /** Every rankup refusal the use case models, mapped to the code a consumer branches on. */
    private static UxmOutcome outcome(RankupResult result) {
        return switch (result.status()) {
            case RANKED_UP -> UxmOutcome.ok();
            case ALREADY_MAX -> UxmOutcome.failed(UxmFailure.ALREADY_IN_STATE, "already on the top rank");
            case REQUIREMENTS_NOT_MET ->
                UxmOutcome.failed(UxmFailure.REFUSED, "the next rank's requirements are not met");
            case CANNOT_AFFORD -> UxmOutcome.failed(UxmFailure.INSUFFICIENT_FUNDS, "the next rank's cost was refused");
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
        };
    }

    /** Refuse when nobody is there, otherwise run {@code write} on the thread that owns the player. */
    private CompletableFuture<UxmOutcome> onPlayer(UUID playerId, Write write) {
        Objects.requireNonNull(playerId, "playerId");
        if (!players.isOnline(playerId)) {
            return CompletableFuture.completedFuture(UxmOutcome.failed(
                    UxmFailure.PLAYER_OFFLINE, "a rank requirement can only be checked against a live player"));
        }
        PlayerRef subject = ApiValues.subject(players, playerId);
        Supplier<UxmOutcome> work = () -> write.apply(subject);
        return AsyncActions.onPlayer(
                scheduler,
                subject,
                work,
                UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player left before the rank could be applied"));
    }

    @FunctionalInterface
    private interface Write {
        UxmOutcome apply(PlayerRef subject);
    }
}
