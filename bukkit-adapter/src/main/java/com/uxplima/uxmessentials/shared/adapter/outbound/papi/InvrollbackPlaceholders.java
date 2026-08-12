package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code invrollback_*} placeholders: when this server last took a snapshot
 * of a player's inventory, and what caused it. It reports only what this enable has captured, because the snapshot
 * table is staff-read history and a HUD refresh must never become a query; a restart therefore starts the answer
 * empty, which is honest rather than wrong.
 *
 * <p>Wired during invrollback wiring; with the module disabled the seam is absent and both keys degrade to the dash.
 */
public interface InvrollbackPlaceholders {

    /** When {@code who}'s last snapshot was written since this enable, or empty when none was. */
    Optional<Instant> lastCapture(PlayerRef who);

    /** What caused it, lowercased ({@code death} or {@code logout}), or empty when none was taken. */
    Optional<String> lastCause(PlayerRef who);
}
