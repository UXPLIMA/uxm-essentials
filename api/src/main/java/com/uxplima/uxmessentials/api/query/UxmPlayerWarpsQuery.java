package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmPlayerWarp;

/**
 * What player warps exist, who owns them, and how many more an owner may create.
 *
 * <p>These are the warps players create for themselves, not the server warps an operator sets; those are
 * {@link UxmWarpsQuery}. A warp that is suspended or archived is still returned by {@link #get(String)} and by
 * {@link #ownedBy(UUID)}, because its owner can still see it; {@link #listPublic()} leaves it out, because nobody
 * else can.
 */
public interface UxmPlayerWarpsQuery {

    /**
     * One page of the active, publicly usable warps, newest first, which is what a browser shows a stranger.
     *
     * <p>Paged rather than whole, because a busy server holds tens of thousands of them and a method that returned
     * all of them would be a full table scan wearing a friendly name. {@code page} counts from zero and
     * {@code pageSize} is 1 to 100; a page past the end is an empty list rather than an error.
     */
    CompletableFuture<List<UxmPlayerWarp>> listPublic(int page, int pageSize);

    /** The warp under this name, whatever its access and status, or empty when there is none. */
    CompletableFuture<Optional<UxmPlayerWarp>> get(String name);

    /** Every warp this player owns, including the private and the suspended ones. */
    CompletableFuture<List<UxmPlayerWarp>> ownedBy(UUID ownerId);

    /** How many warps this player owns. Cheaper than {@link #ownedBy(UUID)} when the count is all you need. */
    CompletableFuture<Integer> count(UUID ownerId);

    /**
     * How many warps this player may own, resolved from their permission nodes and the configured default, which is
     * what the create command enforces. {@link Optional#empty()} means unlimited.
     */
    CompletableFuture<Optional<Integer>> limit(UUID ownerId);
}
