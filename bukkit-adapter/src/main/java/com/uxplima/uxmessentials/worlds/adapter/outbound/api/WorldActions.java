package com.uxplima.uxmessentials.worlds.adapter.outbound.api;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmWorldsActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiActors;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * The published world actions, over the same use cases {@code /world load} and {@code /world unload} run.
 *
 * <p>Both hop to the server's own thread, because loading a world is a server operation and no worker may start
 * one. The future completes when the world is in the state that was asked for, so a plugin that loads a world and
 * then teleports somebody into it can chain the two and know the world is there.
 *
 * <p>The name is turned into a {@link WorldName} before the hop, so a name with a path separator in it throws
 * where the caller is standing rather than arriving as a failure from another thread.
 */
@NullMarked
public final class WorldActions implements UxmWorldsActions {

    private final LoadWorld load;
    private final UnloadWorld unload;
    private final Scheduler scheduler;
    private final String source;

    public WorldActions(WorldApiWrites writes, Scheduler scheduler, String source) {
        Objects.requireNonNull(writes, "writes");
        this.load = writes.load();
        this.unload = writes.unload();
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public CompletableFuture<UxmOutcome> load(String worldName) {
        WorldName target = name(worldName);
        PlayerRef actor = ApiActors.of(source);
        return AsyncActions.onServer(scheduler, () -> outcome(load.load(actor, target)));
    }

    @Override
    public CompletableFuture<UxmOutcome> unload(String worldName) {
        return unload(worldName, true);
    }

    @Override
    public CompletableFuture<UxmOutcome> unload(String worldName, boolean save) {
        WorldName target = name(worldName);
        PlayerRef actor = ApiActors.of(source);
        return AsyncActions.onServer(scheduler, () -> outcome(unload.unload(actor, target, save)));
    }

    private static WorldName name(String worldName) {
        return WorldName.of(Objects.requireNonNull(worldName, "worldName"));
    }

    private static UxmOutcome outcome(Result<Unit, WorldError> result) {
        return result.isErr() ? UxmOutcome.failed(failure(result.errorOrThrow())) : UxmOutcome.ok();
    }

    /** Which published code a world refusal is. */
    private static UxmFailure failure(WorldError error) {
        return switch (error) {
            case NOT_FOUND -> UxmFailure.of(UxmFailure.NOT_FOUND, "no world is registered under that name");
            case FOLDER_MISSING -> UxmFailure.of(UxmFailure.NOT_FOUND, "the world folder is not on disk any more");
            case NOT_A_WORLD_FOLDER ->
                UxmFailure.of(UxmFailure.NOT_FOUND, "that folder does not hold a world the server can read");
            case ALREADY_LOADED -> UxmFailure.of(UxmFailure.ALREADY_IN_STATE, "that world is already loaded");
            case NOT_LOADED -> UxmFailure.of(UxmFailure.ALREADY_IN_STATE, "that world is not loaded");
            case IS_PROTECTED -> UxmFailure.of(UxmFailure.REFUSED, "the default world cannot be unloaded");
            case PLAYERS_PRESENT -> UxmFailure.of(UxmFailure.REFUSED, "there are still players in that world");
            case IO_ERROR -> UxmFailure.of(UxmFailure.FAILED, "the server could not read or write the world folder");
            default ->
                UxmFailure.of(
                        UxmFailure.REFUSED, "worlds refused it: " + error.name().toLowerCase(java.util.Locale.ROOT));
        };
    }
}
