package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import org.jspecify.annotations.NullMarked;

/**
 * A permission plugin's stored nodes, read for a player who need not be online. This is the seam that keeps LuckPerms
 * and Vault soft dependencies for {@link ServerStoredPermissions}: the implementations that name them are bound only
 * past their plugin-present guards.
 */
@NullMarked
interface NodeStore {

    /**
     * Start a read of {@code node} for {@code player}. Called on a server thread: whatever the store needs from the
     * server is read there, and the returned load runs off the tick.
     */
    Load prepare(UUID player, String node);

    /** The off-tick half of a read. */
    @FunctionalInterface
    interface Load {

        /**
         * What this store grants: {@link Answer#HELD} when it grants the node, {@link Answer#NOT_HELD} when it grants
         * nothing (a node it does not set, a denial, a player it holds no data for: never proof the node is not
         * held elsewhere), {@link Answer#NO_SOURCE} when it cannot look this player up at all. A load that fails
         * counts as {@link Answer#NO_ANSWER}. It may load from the store's database and answer later.
         */
        CompletableFuture<Answer> run();
    }
}
