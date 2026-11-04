package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmRegion;

/**
 * The guard module's regions, read through this plugin.
 *
 * <p>Every answer is a future so callers never depend on which thread holds the regions. There is no write surface
 * here on purpose: editing a region is an operator act with its own command, permissions and history.
 *
 * <p>A region's {@code flags} are its rules written as {@code <action> <target>} with the value {@code allow} or
 * {@code deny}, followed by its behaviours by key.
 */
public interface UxmRegionsQuery {

    /**
     * Whether the guard module is running.
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
