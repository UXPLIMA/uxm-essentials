package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmSnapshot;

/**
 * The inventory snapshots the server is holding for a player.
 *
 * <p>Newest first, which is the order a staff member reads them in and the order the retention rules prune from
 * the other end. The set is already bounded by those rules, per player and by age, so there is no limit to pass:
 * what you get is what the server kept.
 *
 * <p>The items themselves are not published. A snapshot's contents are serialized Bukkit item stacks, and there is
 * no honest way to hand them across this boundary; what is published is enough to list them and to name one for a
 * restore.
 */
public interface UxmInvRollbackQuery {

    /** Every snapshot held for this player, newest first; empty for a player with none. */
    CompletableFuture<List<UxmSnapshot>> of(UUID playerId);
}
