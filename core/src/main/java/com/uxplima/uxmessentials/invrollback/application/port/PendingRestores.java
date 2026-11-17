package com.uxplima.uxmessentials.invrollback.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.domain.Snapshot;

/**
 * The restores staff chose for a player who was not online, kept in the shared database until the player next joins
 * a server of it. Each keeps a full copy of the chosen snapshot, so the retention sweep pruning the snapshot meanwhile
 * does not lose the restore.
 */
public interface PendingRestores {

    /** Keep {@code snapshot} to be put on {@code player} as they next join. */
    void keep(UUID player, Snapshot snapshot);

    /**
     * Take the restores kept for {@code player}, oldest first, removing them. A restore is handed to one taker only,
     * so two servers taking at once never apply the same one twice.
     */
    List<Snapshot> take(UUID player);
}
