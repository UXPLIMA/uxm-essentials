package com.uxplima.uxmessentials.shared.application.port;

import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Reads a node for a player who is not online on this server (offline, or on another backend of the network), from
 * what the permission plugin has stored for them rather than from a live player.
 *
 * <p>Call it on a server thread. It never waits there: what it needs from the server it reads in place, and a load
 * from the permission plugin's storage runs off the tick and answers later. The future never completes exceptionally.
 */
public interface StoredPermissions {

    /** What this server holds for {@code who} under {@code node}. */
    CompletableFuture<Answer> read(PlayerRef who, String node);

    /** The answer to one stored read. */
    enum Answer {
        /** The node is held. */
        HELD,
        /** The node is not held. */
        NOT_HELD,
        /** A source exists but did not answer (the storage failed or was slow); asking again may work. */
        NO_ANSWER,
        /**
         * Nothing on this server can read the node for this player: a permission plugin whose stored nodes cannot be
         * read here, or a backend whose own grants this server cannot see. Asking again will not help.
         */
        NO_SOURCE
    }
}
