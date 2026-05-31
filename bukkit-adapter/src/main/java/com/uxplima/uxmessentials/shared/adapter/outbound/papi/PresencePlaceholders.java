package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code afk}, {@code afk_duration} and {@code vanished}
 * placeholders. It is an adapter over the presence context's in-memory {@code PresenceStore} wired during
 * bootstrap; when the presence module is disabled the seam is absent and the placeholders degrade.
 *
 * <p>Presence is per-session in-memory state. An offline player has no tracked presence, so the seam
 * returns an empty snapshot — the expansion renders the offline/empty default for it.
 */
public interface PresencePlaceholders {

    /** A point-in-time view of {@code who}'s presence, or empty when they are not currently tracked. */
    Optional<Snapshot> snapshot(PlayerRef who);

    /**
     * The immutable presence facts the placeholders read: whether the player is AFK, how long they have
     * been AFK as of the read, and whether they are vanished.
     *
     * @param afk whether {@code who} is currently AFK
     * @param afkFor how long {@code who} has been AFK as of the read, {@link Duration#ZERO} when not AFK
     * @param vanished whether {@code who} is currently vanished
     */
    record Snapshot(boolean afk, Duration afkFor, boolean vanished) {

        public Snapshot {
            java.util.Objects.requireNonNull(afkFor, "afkFor");
        }
    }
}
