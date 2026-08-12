package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code nametags_*} placeholders: which of the authored formats the player
 * currently wears above their head, and whether they wear one at all. A player who matches no format wears nothing,
 * which is a different state from wearing an empty one and worth being able to tell apart.
 *
 * <p>Like the tablist seam it reports what the presenter last applied rather than re-selecting. Wired during
 * nametags wiring; with the module disabled the seam is absent and the keys degrade to the dash.
 */
public interface NametagsPlaceholders {

    /** The format {@code who} wears, or empty when they wear none. */
    Optional<String> format(PlayerRef who);
}
