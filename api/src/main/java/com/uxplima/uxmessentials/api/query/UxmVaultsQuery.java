package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmVault;

/**
 * What vaults a player owns, and how many more they may open.
 *
 * <p>Vault numbers count from one, which is what the owner types. Nothing here returns the contents: those are
 * Bukkit item stacks, and a consumer that needs them should open the vault through the plugin rather than read
 * around it.
 */
public interface UxmVaultsQuery {

    /** Every vault this player has opened, in number order. Empty when they have none. */
    CompletableFuture<List<UxmVault>> list(UUID ownerId);

    /** The vault under this number, counting from one, or empty when the player never opened it. */
    CompletableFuture<Optional<UxmVault>> get(UUID ownerId, int index);

    /** How many vaults this player has opened. Cheaper than {@link #list(UUID)} when the count is all you need. */
    CompletableFuture<Integer> count(UUID ownerId);

    /**
     * How many vaults this player may open, resolved from their permission nodes and the configured default.
     * {@link Optional#empty()} means unlimited.
     */
    CompletableFuture<Optional<Integer>> limit(UUID ownerId);

    /**
     * How many rows each of this player's vaults holds, resolved the same way, between one and six. This is the
     * size the next vault they open would have.
     */
    CompletableFuture<Integer> rows(UUID ownerId);
}
