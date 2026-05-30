package com.uxplima.uxmessentials.shared.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * Outbound port that resolves world identities without exposing a Bukkit {@code World}.
 *
 * <p>The teleport context resolves destination worlds, spawn-mirror targets, and the per-world RTP
 * queue's world through this port, mapping each to a {@link WorldRef}. A name or uid that no longer
 * maps to a loaded world returns empty.
 */
public interface WorldLookup {

    /** The loaded world with this exact name, if any. */
    Optional<WorldRef> findByName(String name);

    /** The loaded world with this persistent uid, if any. */
    Optional<WorldRef> findByUid(UUID uid);
}
