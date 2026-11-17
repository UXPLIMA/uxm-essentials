package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
         * Whether the player is granted ({@code true}) or denied ({@code false}) the node by the stored data, or empty
         * when the store sets nothing for it and the server's own default applies. It may load from the store's
         * database and answer later.
         */
        CompletableFuture<Optional<Boolean>> run();
    }
}
