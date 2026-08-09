package com.uxplima.uxmessentials.api.action;

import java.util.concurrent.CompletableFuture;

/**
 * Bringing a registered world into the server, or taking it back out.
 *
 * <p>These are the two operations {@code /world load} and {@code /world unload} run, over the same registry: a
 * world has to be registered with uxmEssentials before it can be loaded by name, and creating one is a heavier
 * thing than an API call should do quietly, so it is not published here.
 *
 * <p>Both refuse rather than force. A world that is already loaded, a world that is not, the default world, and a
 * world with players still inside all come back as a failure with a code to branch on. The unload keeps the
 * region files by default; ask for the other one only if you mean it.
 *
 * <pre>{@code
 * actions.worlds().ifPresent(worlds ->
 *     worlds.load("event_arena").thenAccept(outcome ->
 *         outcome.ifFailed(failure -> getLogger().warning(failure.message()))));
 * }</pre>
 */
public interface UxmWorldsActions {

    /** Load this registered world. Fails when it is unknown, already loaded, or its folder has gone. */
    CompletableFuture<UxmOutcome> load(String worldName);

    /** Unload this world, saving it first. Fails when it is not loaded, is protected, or still holds players. */
    CompletableFuture<UxmOutcome> unload(String worldName);

    /**
     * Unload this world, saving it first only when {@code save} is true.
     *
     * <p>Unloading without saving throws away everything that changed since the last save, which is what an
     * arena world rebuilt on every round wants and what nothing else does.
     */
    CompletableFuture<UxmOutcome> unload(String worldName, boolean save);
}
