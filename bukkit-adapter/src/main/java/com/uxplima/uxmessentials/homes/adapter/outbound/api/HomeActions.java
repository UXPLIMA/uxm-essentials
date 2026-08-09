package com.uxplima.uxmessentials.homes.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmHomeActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The published home actions, over the same use cases {@code /sethome} and {@code /delhome} run.
 *
 * <p>The use cases here are wired free of charge: a home cost is what a player pays for typing the command, and a
 * plugin setting a home on their behalf is not the player. Everything else is the command's path, gates and events
 * included, so a listener that vetoes a create refuses the API create too.
 *
 * <p>The write and the read-back happen on one worker hop rather than two, so nothing can slip between creating a
 * home and reporting it.
 */
@NullMarked
public final class HomeActions implements UxmHomeActions {

    private final CreateHomeAtSlot create;
    private final RelocateHome relocate;
    private final RenameHome rename;
    private final DeleteHome delete;
    private final HomeRepository repository;
    private final PlayerLookup players;
    private final WorldLookup worlds;
    private final Scheduler scheduler;

    public HomeActions(
            HomeApiWrites writes,
            HomeRepository repository,
            PlayerLookup players,
            WorldLookup worlds,
            Scheduler scheduler) {
        Objects.requireNonNull(writes, "writes");
        this.create = writes.create();
        this.relocate = writes.relocate();
        this.rename = writes.rename();
        this.delete = writes.delete();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmResult<UxmHome>> set(UUID ownerId, int slot, UxmLocation location) {
        return write(ownerId, slot, location, create::create);
    }

    @Override
    public CompletableFuture<UxmResult<UxmHome>> relocate(UUID ownerId, int slot, UxmLocation location) {
        return write(ownerId, slot, location, relocate::relocate);
    }

    @Override
    public CompletableFuture<UxmOutcome> rename(UUID ownerId, int slot, String label) {
        PlayerRef owner = subject(ownerId);
        HomeSlot at = slot(slot);
        HomeLabel named = HomeLabel.of(Objects.requireNonNull(label, "label"));
        return AsyncActions.perform(scheduler, () -> outcome(rename.rename(owner, at, Optional.of(named))));
    }

    @Override
    public CompletableFuture<UxmOutcome> delete(UUID ownerId, int slot) {
        PlayerRef owner = subject(ownerId);
        HomeSlot at = slot(slot);
        return AsyncActions.perform(scheduler, () -> outcome(delete.delete(owner, at)));
    }

    /** Create and relocate differ only in which use case runs, and both answer with the home as it now stands. */
    private CompletableFuture<UxmResult<UxmHome>> write(UUID ownerId, int slot, UxmLocation location, Placement place) {
        PlayerRef owner = subject(ownerId);
        HomeSlot at = slot(slot);
        Objects.requireNonNull(location, "location");
        return AsyncActions.perform(scheduler, () -> {
            Optional<Position> position = ApiValues.position(worlds, location);
            if (position.isEmpty()) {
                return UxmResult.failed(UxmFailure.NOT_FOUND, "no loaded world named " + location.world());
            }
            Result<Unit, HomeError> result = place.apply(owner, at, position.get());
            if (result.isErr()) {
                return UxmResult.failed(failure(result.errorOrThrow()));
            }
            return repository
                    .findSlot(owner, at)
                    .map(home -> UxmResult.ok(HomeQueries.view(home)))
                    .orElseGet(() -> UxmResult.failed(UxmFailure.FAILED, "the home was written but could not be read"));
        });
    }

    /** Which published code a homes refusal is. */
    private static UxmFailure failure(HomeError error) {
        return switch (error) {
            case NOT_FOUND -> UxmFailure.of(UxmFailure.NOT_FOUND, "no home in that slot");
            case SLOT_TAKEN -> UxmFailure.of(UxmFailure.ALREADY_EXISTS, "that slot already holds a home");
            case LIMIT_REACHED -> UxmFailure.of(UxmFailure.REFUSED, "the player is at their home limit");
            case VETOED -> UxmFailure.of(UxmFailure.CANCELLED, "another plugin refused it");
            default ->
                UxmFailure.of(
                        UxmFailure.REFUSED, "homes refused it: " + error.name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    private static UxmOutcome outcome(Result<Unit, HomeError> result) {
        return result.isErr() ? UxmOutcome.failed(failure(result.errorOrThrow())) : UxmOutcome.ok();
    }

    private PlayerRef subject(UUID ownerId) {
        return ApiValues.subject(players, Objects.requireNonNull(ownerId, "ownerId"));
    }

    private static HomeSlot slot(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("home slot must not be negative: " + slot);
        }
        return HomeSlot.of(slot);
    }

    /** The shape the create and relocate use cases share, so one method can run either. */
    @FunctionalInterface
    private interface Placement {

        Result<Unit, HomeError> apply(PlayerRef owner, HomeSlot slot, Position at);
    }
}
