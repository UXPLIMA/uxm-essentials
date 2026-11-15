package com.uxplima.uxmessentials.shared.application.hop.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the network-shared record of in-flight cross-server hops, one row per player. The origin
 * records the hop before handing the player to the proxy and the target reads it back on join, so the
 * implementation must be visible to every backend of the network (a platform boundary). Every method is a single
 * statement keyed by the player.
 */
public interface PendingHopStore {

    /**
     * Upsert {@code pending} as the player's single in-flight hop, replacing any earlier row for them. A hop the
     * store cannot hold is refused with an exception, which the caller turns into a refund.
     */
    void record(PendingHop pending);

    /** The player's in-flight hop, if one is recorded. */
    Optional<PendingHop> find(UUID player);

    /**
     * Delete {@code pending}'s row in one statement, only while the stored row is still that same hop (same
     * player, kind, target, target server and request time to the millisecond), and return how many rows went:
     * {@code 1} or {@code 0}. The delete is the claim on the hop. A caller moves or refunds the player only when it
     * removed the row, so two quick joins, or a join racing the origin's watchdog, can never act twice, and a
     * decision taken on an old read can never wipe a newer hop the player started since.
     */
    int clear(PendingHop pending);
}
