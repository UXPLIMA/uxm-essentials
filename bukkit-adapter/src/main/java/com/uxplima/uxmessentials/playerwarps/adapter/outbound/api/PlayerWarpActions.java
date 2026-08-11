package com.uxplima.uxmessentials.playerwarps.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmPlayerWarpsActions;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
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
 * The published player-warp writes, over the same use cases the commands run.
 *
 * <p>Which means the same per-warp roles. A warp has an owner, and may have co-owners and managers; each verb here
 * asks the same question the command asks about the player it is acting as, so a plugin cannot quietly move or
 * delete somebody else's warp by going through the API instead of the command.
 *
 * <p>Every verb touches the database and nothing else, so they run on a worker thread. Turning a world name into a
 * world happens first, on the calling thread, because a name no loaded world answers to is an answer rather than
 * work worth scheduling.
 */
@NullMarked
public final class PlayerWarpActions implements UxmPlayerWarpsActions {

    private final SetPlayerWarp setWarp;
    private final EditPlayerWarp edit;
    private final ArchivePlayerWarp archiver;
    private final PlayerLookup players;
    private final WorldLookup worlds;
    private final Scheduler scheduler;

    public PlayerWarpActions(
            SetPlayerWarp setWarp,
            EditPlayerWarp edit,
            ArchivePlayerWarp archiver,
            PlayerLookup players,
            WorldLookup worlds,
            Scheduler scheduler) {
        this.setWarp = Objects.requireNonNull(setWarp, "setWarp");
        this.edit = Objects.requireNonNull(edit, "edit");
        this.archiver = Objects.requireNonNull(archiver, "archiver");
        this.players = Objects.requireNonNull(players, "players");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> create(UUID actorId, String name, UxmLocation where) {
        return at(
                where,
                position -> named(actorId, name, (actor, warp) -> setWarp.set(actor, actor.name(), warp, position)));
    }

    @Override
    public CompletableFuture<UxmOutcome> relocate(UUID actorId, String name, UxmLocation where) {
        return at(where, position -> named(actorId, name, (actor, warp) -> edit.moveHere(actor, warp, position)));
    }

    @Override
    public CompletableFuture<UxmOutcome> rename(UUID actorId, String name, String newName) {
        Objects.requireNonNull(newName, "newName");
        Optional<PlayerWarpName> renamed = shaped(newName);
        if (renamed.isEmpty()) {
            return completed(malformed(newName));
        }
        return named(actorId, name, (actor, warp) -> edit.rename(actor, warp, renamed.get()));
    }

    @Override
    public CompletableFuture<UxmOutcome> archive(UUID actorId, String name) {
        return named(actorId, name, archiver::archive);
    }

    @Override
    public CompletableFuture<UxmOutcome> restore(UUID actorId, String name) {
        return named(actorId, name, archiver::restore);
    }

    @Override
    public CompletableFuture<UxmOutcome> delete(UUID actorId, String name) {
        return named(actorId, name, archiver::hardDelete);
    }

    /** Resolve the world before scheduling anything: a world nobody loaded is an answer, not work. */
    private CompletableFuture<UxmOutcome> at(
            UxmLocation where, Function<Position, CompletableFuture<UxmOutcome>> body) {
        Objects.requireNonNull(where, "where");
        return ApiValues.position(worlds, where)
                .map(body)
                .orElseGet(() -> completed(
                        UxmOutcome.failed(UxmFailure.NOT_FOUND, "no loaded world is named " + where.world())));
    }

    /** Check the name's shape and resolve the actor, then run {@code body} on a worker thread. */
    private CompletableFuture<UxmOutcome> named(UUID actorId, String name, Write body) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarpName> warp = shaped(name);
        if (warp.isEmpty()) {
            return completed(malformed(name));
        }
        PlayerRef actor = ApiValues.subject(players, actorId);
        return perform(() -> body.apply(actor, warp.get()));
    }

    private CompletableFuture<UxmOutcome> perform(Supplier<Result<Unit, PlayerWarpError>> body) {
        return AsyncActions.perform(scheduler, () -> {
            Result<Unit, PlayerWarpError> done = body.get();
            return done.isErr() ? refusal(done.errorOrThrow()) : UxmOutcome.ok();
        });
    }

    /** The name as the plugin stores it, or empty when its shape is one no warp can have. */
    private static Optional<PlayerWarpName> shaped(String raw) {
        try {
            return Optional.of(PlayerWarpName.of(raw));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static CompletableFuture<UxmOutcome> completed(UxmOutcome outcome) {
        return CompletableFuture.completedFuture(outcome);
    }

    private static UxmOutcome malformed(String name) {
        return UxmOutcome.failed(
                UxmFailure.REFUSED,
                "a player warp name is 3 to 32 characters of a-z, 0-9, _ and -, and this is not: " + name);
    }

    private static UxmOutcome refusal(PlayerWarpError error) {
        return switch (error) {
            case NOT_FOUND -> UxmOutcome.failed(UxmFailure.NOT_FOUND, "no warp goes by that name");
            case NAME_TAKEN -> UxmOutcome.failed(UxmFailure.ALREADY_EXISTS, "another player already holds that name");
            case LIMIT_REACHED -> UxmOutcome.failed(UxmFailure.REFUSED, "the owner is at their player-warp limit");
            case NO_PERMISSION -> UxmOutcome.failed(UxmFailure.REFUSED, "that player may not do this to this warp");
            case RESERVED_NAME -> UxmOutcome.failed(UxmFailure.REFUSED, "that name is reserved");
            case WORLD_BLACKLISTED ->
                UxmOutcome.failed(UxmFailure.REFUSED, "the operator does not allow player warps in that world");
            case UNSAFE_LOCATION -> UxmOutcome.failed(UxmFailure.REFUSED, "there is nowhere safe to stand there");
            case SPONSORED_LOCKED ->
                UxmOutcome.failed(UxmFailure.REFUSED, "the warp is sponsored, and sponsorship holds it as it is");
            case VETOED -> UxmOutcome.failed(UxmFailure.CANCELLED, "another plugin refused it");
            default -> UxmOutcome.failed(UxmFailure.REFUSED, "the warp module refused it: " + error.name());
        };
    }

    /** One player-warp write, named so the three call sites read the same. */
    @FunctionalInterface
    private interface Write {
        Result<Unit, PlayerWarpError> apply(PlayerRef actor, PlayerWarpName name);
    }
}
