package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmRegion;

/**
 * What WorldGuard protects, read through this plugin.
 *
 * <p>A convenience rather than a second source of truth. WorldGuard owns region state and has its own API; this is
 * here so a plugin that already depends on us can ask "what covers this spot" without taking a second dependency
 * and writing the reflective walk a third time.
 *
 * <p>Every answer is a future because the region container is read on the server thread rather than on a worker.
 * WorldGuard's region maps are live server state, and reading them from a pool thread is not something its API
 * promises to survive.
 *
 * <p>There is no write surface here on purpose. Editing a region is an operator act with its own command, its own
 * permissions and its own audit trail, and a silent edit from a plugin would leave staff looking at a protection
 * nobody in the logs ever changed.
 */
public interface UxmRegionsQuery {

    /**
     * Whether WorldGuard is installed and reachable.
     *
     * <p>Answered on the calling thread, because it is the question you ask before the others. When this is false
     * every read below answers empty rather than failing, so a caller that skips this check still behaves.
     */
    boolean available();

    /** Every region defined in a world, in no particular order; empty when the world is unknown or has none. */
    CompletableFuture<List<UxmRegion>> in(String worldName);

    /** One region by id, or empty when that world has no region with it. */
    CompletableFuture<Optional<UxmRegion>> region(String worldName, String id);

    /**
     * Every region covering a point, highest priority first.
     *
     * <p>The order is the one that decides an overlap, so the first entry is the region whose flags win. A point in
     * a world that is not loaded answers empty.
     */
    CompletableFuture<List<UxmRegion>> at(UxmLocation where);
}
