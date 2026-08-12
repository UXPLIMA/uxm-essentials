package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code tablist_*} placeholders: which of the authored formats the player's
 * tab is currently drawn from. An operator gives one group a different tab format and then wants a line, a chat
 * prefix or a hologram to agree with what that player sees; this is what lets them read it.
 *
 * <p>It reports what the renderer last applied rather than re-selecting, so it costs nothing per read and cannot
 * re-evaluate a format condition that itself expands a placeholder. Wired during tablist wiring; with the module
 * disabled the seam is absent and the keys degrade to the dash.
 */
public interface TablistPlaceholders {

    /** The format {@code who}'s tab is drawn from, or empty when the renderer is drawing them none. */
    Optional<String> format(PlayerRef who);
}
