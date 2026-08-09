package com.uxplima.uxmessentials.warps.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.action.UxmWarpActions;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmWarp;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiActors;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.DelWarp;
import com.uxplima.uxmessentials.warps.application.MoveWarp;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * The published warp actions, over the same use cases {@code /setwarp}, {@code /movewarp} and {@code /delwarp} run.
 *
 * <p>The command's {@code set} both creates and moves, which is right at a keyboard and wrong in an API: a plugin
 * asking to create something that already exists has made a mistake worth hearing about. So the two are split here,
 * each refusing the other's case, and the existence check happens on the same worker hop as the write.
 *
 * <p>A warp created this way records the calling plugin as its owner, which is what {@code /warp info} will show.
 * Nobody holds that identity, so nothing else about the server changes.
 */
@NullMarked
public final class WarpActions implements UxmWarpActions {

    private final SetWarp set;
    private final MoveWarp move;
    private final DelWarp delete;
    private final WarpRepository repository;
    private final WorldLookup worlds;
    private final Scheduler scheduler;
    private final PlayerRef actor;

    public WarpActions(
            SetWarp set,
            MoveWarp move,
            DelWarp delete,
            WarpRepository repository,
            WorldLookup worlds,
            Scheduler scheduler,
            String source) {
        this.set = Objects.requireNonNull(set, "set");
        this.move = Objects.requireNonNull(move, "move");
        this.delete = Objects.requireNonNull(delete, "delete");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.actor = ApiActors.of(Objects.requireNonNull(source, "source"));
    }

    @Override
    public CompletableFuture<UxmResult<UxmWarp>> create(String name, UxmLocation location) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        return AsyncActions.perform(scheduler, () -> {
            Optional<WarpName> parsed = WarpQueries.parse(name);
            if (parsed.isEmpty()) {
                return UxmResult.failed(UxmFailure.REFUSED, "not a legal warp name: " + name);
            }
            if (repository.exists(parsed.get())) {
                return UxmResult.failed(UxmFailure.ALREADY_EXISTS, "a warp named " + name + " already exists");
            }
            return place(parsed.get(), location, (warp, at) -> set.set(actor, warp, at));
        });
    }

    @Override
    public CompletableFuture<UxmResult<UxmWarp>> move(String name, UxmLocation location) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        return AsyncActions.perform(scheduler, () -> {
            Optional<WarpName> parsed = WarpQueries.parse(name);
            if (parsed.isEmpty() || !repository.exists(parsed.get())) {
                return UxmResult.failed(UxmFailure.NOT_FOUND, "no warp named " + name);
            }
            return place(parsed.get(), location, (warp, at) -> move.move(actor, warp, at));
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> delete(String name) {
        Objects.requireNonNull(name, "name");
        return AsyncActions.perform(
                scheduler,
                () -> WarpQueries.parse(name)
                        .map(parsed -> {
                            Result<Unit, WarpError> result = delete.delete(actor, parsed);
                            return result.isErr() ? UxmOutcome.failed(failure(result.errorOrThrow())) : UxmOutcome.ok();
                        })
                        .orElseGet(() -> UxmOutcome.failed(UxmFailure.NOT_FOUND, "no warp named " + name)));
    }

    /** Resolve the world, run the write, and answer with the warp as it now stands. */
    private UxmResult<UxmWarp> place(WarpName name, UxmLocation location, Placement place) {
        Optional<Position> position = ApiValues.position(worlds, location);
        if (position.isEmpty()) {
            return UxmResult.failed(UxmFailure.NOT_FOUND, "no loaded world named " + location.world());
        }
        Result<Unit, WarpError> result = place.apply(name, position.get());
        if (result.isErr()) {
            return UxmResult.failed(failure(result.errorOrThrow()));
        }
        return repository
                .find(name)
                .map(warp -> UxmResult.ok(WarpQueries.view(warp)))
                .orElseGet(() -> UxmResult.failed(UxmFailure.FAILED, "the warp was written but could not be read"));
    }

    /** Which published code a warps refusal is. */
    private static UxmFailure failure(WarpError error) {
        return switch (error) {
            case NOT_FOUND, NONE_SET -> UxmFailure.of(UxmFailure.NOT_FOUND, "no warp by that name");
            case NAME_TAKEN -> UxmFailure.of(UxmFailure.ALREADY_EXISTS, "a warp by that name already exists");
            case VETOED -> UxmFailure.of(UxmFailure.CANCELLED, "another plugin refused it");
            default ->
                UxmFailure.of(
                        UxmFailure.REFUSED, "warps refused it: " + error.name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    /** The shape the set and move use cases share, so one method can run either. */
    @FunctionalInterface
    private interface Placement {

        Result<Unit, WarpError> apply(WarpName name, Position at);
    }
}
