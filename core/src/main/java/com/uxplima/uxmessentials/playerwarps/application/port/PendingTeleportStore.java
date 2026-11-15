package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the network-shared record of in-flight cross-server teleports. It backs one row per player in
 * the {@code player_warp_pending_teleports} table (PK {@code player_uuid}), so the origin backend records the
 * intent before handing the player across the proxy and the target backend reads it back on join. Every method is
 * a single indexed statement keyed by the player uuid, never a scan.
 */
public interface PendingTeleportStore {

    /**
     * Upsert {@code pending} as the player's single in-flight teleport, replacing any earlier row for them. The
     * origin backend calls this immediately before the proxy connect, so a fresh request always overwrites a stale
     * one rather than accumulating rows.
     */
    void record(PendingTeleport pending);

    /** The player's in-flight teleport, if one is recorded. A single PK read. */
    Optional<PendingTeleport> find(UUID player);

    /**
     * Delete {@code pending}'s row in one statement, only while the stored row is still that same request (same
     * player, warp, target and request time to the millisecond), and return how many rows went: {@code 1} or
     * {@code 0}. The delete is the claim on the request. A caller moves or refunds the player only when it removed
     * the row, so two quick joins, or a join racing the origin's watchdog, can never act twice, and a decision
     * taken on an old read can never wipe a newer request the player made since.
     */
    int clear(PendingTeleport pending);
}
