package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmHome;

/**
 * What a player's homes are, and how many more they may set.
 *
 * <p>Homes are stored per player and keyed by slot, counting from zero. The number a player sees in chat and in the
 * grid is one higher, which {@link UxmHome#slotNumber()} gives you, so use it whenever you display one.
 */
public interface UxmHomesQuery {

    /** Every home this player owns, in slot order. Empty when they have none. */
    CompletableFuture<List<UxmHome>> list(UUID playerId);

    /** The home in this slot, counting from zero, or empty when the slot is free. */
    CompletableFuture<Optional<UxmHome>> get(UUID playerId, int slot);

    /** How many homes this player has set. Cheaper than {@link #list(UUID)} when the count is all you need. */
    CompletableFuture<Integer> count(UUID playerId);

    /**
     * How many homes this player may have, resolved from their permission nodes and the configured default, which is
     * what {@code /sethome} enforces. {@link Optional#empty()} means unlimited.
     */
    CompletableFuture<Optional<Integer>> limit(UUID playerId);
}
