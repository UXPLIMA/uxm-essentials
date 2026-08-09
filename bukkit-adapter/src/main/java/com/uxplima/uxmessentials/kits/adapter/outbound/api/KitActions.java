package com.uxplima.uxmessentials.kits.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmKitActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The published kit actions: one verb that hands the items over, one that runs the player's own path.
 *
 * <p>{@code give} goes straight to the granter, which is what makes it ungated: no permission, no cooldown stamp,
 * no cost, and a one-time kit is still unclaimed afterwards. {@code claim} runs {@link ClaimKit} instead, so every
 * gate the command applies applies here and the refusal says which one.
 *
 * <p>Both land on the recipient's own thread, because both end in items going into a live inventory. A player who
 * leaves in between is reported as offline rather than leaving a caller waiting on a future that never completes.
 */
@NullMarked
public final class KitActions implements UxmKitActions {

    private final KitRepository repository;
    private final KitGranter granter;
    private final ClaimKit claim;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public KitActions(
            KitRepository repository, KitGranter granter, ClaimKit claim, PlayerLookup players, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.granter = Objects.requireNonNull(granter, "granter");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> give(UUID playerId, String kitId) {
        return onRecipient(playerId, kitId, (recipient, kit) -> {
            granter.grant(recipient, kit);
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> claim(UUID playerId, String kitId) {
        return onRecipient(playerId, kitId, (recipient, kit) -> {
            Result<Unit, KitError> result = claim.claim(recipient, kit.id());
            return result.isErr() ? UxmOutcome.failed(failure(result.errorOrThrow())) : UxmOutcome.ok();
        });
    }

    /** Resolve the player and the kit, then run {@code hand} on the thread that owns the player's inventory. */
    private CompletableFuture<UxmOutcome> onRecipient(UUID playerId, String kitId, Handout hand) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        if (!players.isOnline(playerId)) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the items need somewhere to go"));
        }
        Optional<KitDefinition> kit = parse(kitId).flatMap(repository::find);
        if (kit.isEmpty()) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.NOT_FOUND, "no kit with the id " + kitId));
        }
        PlayerRef recipient = ApiValues.subject(players, playerId);
        return AsyncActions.onPlayer(
                scheduler,
                recipient,
                () -> hand.apply(recipient, kit.get()),
                UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player left before the kit could be handed over"));
    }

    /** Which published code a kits refusal is. */
    private static UxmFailure failure(KitError error) {
        return switch (error) {
            case NOT_FOUND, NONE_DEFINED -> UxmFailure.of(UxmFailure.NOT_FOUND, "no kit by that id");
            case CANNOT_AFFORD -> UxmFailure.of(UxmFailure.INSUFFICIENT_FUNDS, "the player cannot pay for the kit");
            case VETOED, CANCELLED -> UxmFailure.of(UxmFailure.CANCELLED, "another plugin refused it");
            default ->
                UxmFailure.of(
                        UxmFailure.REFUSED, "kits refused it: " + error.name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    private static Optional<KitId> parse(String kitId) {
        try {
            return Optional.of(KitId.of(kitId));
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }

    /** What each verb does once the player and the kit are both in hand. */
    @FunctionalInterface
    private interface Handout {

        UxmOutcome apply(PlayerRef recipient, KitDefinition kit);
    }
}
