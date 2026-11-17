package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.NullMarked;

/**
 * A permission plugin's stored nodes, read for a player who need not be online. This is the seam that keeps LuckPerms
 * a soft dependency for {@link ServerStoredPermissions}: the only implementation that names LuckPerms is bound past
 * the plugin-present guard.
 */
@NullMarked
interface NodeStore {

    /**
     * Whether {@code player} is granted ({@code true}) or denied ({@code false}) {@code node} by the stored data, or
     * empty when the store sets nothing for it and the server's own default applies. Called off the server thread; it
     * may load from the store's database and answer later.
     */
    CompletableFuture<Optional<Boolean>> load(UUID player, String node);
}
